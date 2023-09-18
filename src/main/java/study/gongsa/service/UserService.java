package study.gongsa.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import study.gongsa.domain.User;
import study.gongsa.dto.MailDto;
import study.gongsa.dto.MyPageUserResponse;
import study.gongsa.dto.UserMyPageInfo;
import study.gongsa.repository.UserRepository;
import study.gongsa.support.mail.CodeGenerator;
import study.gongsa.support.exception.IllegalStateExceptionWithLocation;
import study.gongsa.support.mail.GmailSender;

import java.sql.Timestamp;
import java.util.*;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor

public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final GmailSender gmailSender;
    private final CodeGenerator codeGenerator;
    private final ImageService imageService;

    public int join(User user){
        //이메일 중복 체크
        Optional<User> userByEmail = userRepository.findByEmail(user.getEmail());
        userByEmail.ifPresent(m -> {
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST,"email","중복된 이메일입니다.");
        });

        //닉네임 중복 체크
        Optional<User> userByNickname = userRepository.findByNickname(user.getNickname());
        userByNickname.ifPresent(m -> {
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST,"nickname","중복된 닉네임입니다.");
        });

        //비밀번호 암호화
        String encryptedPassword = passwordEncoder.encode(user.getPasswd());
        user.setPasswd(encryptedPassword);

        //랜덤 이미지 설정
        String fileName = "r"+codeGenerator.generateRandomNumber(1)+"jpg";
        user.setImgPath(fileName);

        return userRepository.save(user).intValue();
    }

    public void sendJoinMail(String email){
        //이메일 이미 인증된 사용자인 경우, 존재하지 않는 이메일인 경우
        Optional<User> userByEmail = userRepository.findByEmail(email);
        if (userByEmail.isEmpty()){
            throw new IllegalStateExceptionWithLocation(HttpStatus.UNAUTHORIZED, null,"가입되지 않은 이메일입니다.");
        }
        User user = userByEmail.get();
        if (user.getIsAuth()){
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST, null, "이미 인증된 사용자입니다.");
        }

        //인증번호 생성
        String authCode = codeGenerator.generateRandomNumber(6);

        //이메일 전송
        MailDto mailDto = new MailDto();
        mailDto.setRegisterMailForm(user.getEmail(),user.getNickname(),authCode);
        gmailSender.sendMail(mailDto);

        //user 정보 업데이트
        userRepository.updateAuthCode(authCode, new Timestamp(new Date().getTime()), user.getUID());
    }

    public void sendChangePasswdMail(String email){
        //존재하지 않는 이메일인 경우
        Optional<User> userByEmail = userRepository.findByEmail(email);
        if (userByEmail.isEmpty()){
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST, "email","등록되지 않은 이메일입니다.");
        }
        User user = userByEmail.get();

        //임시 비밀번호 생성
        String passwdCode = codeGenerator.generateRandomString(8);
        String encryptedPassword = passwordEncoder.encode(passwdCode);

        //user 정보 업데이트
        userRepository.updatePasswd(encryptedPassword, new Timestamp(new Date().getTime()), user.getUID());

        //이메일 전송
        MailDto mailDto = new MailDto();
        mailDto.setChangePasswdMailForm(user.getEmail(),user.getNickname(),passwdCode);
        gmailSender.sendMail(mailDto);
    }

    public void changePasswd(int uid, String currentPasswd, String nextPasswd){
        // 존재하지 않는 회원일 경우, 비밀번호가 올바르지 않을 경우, 비밀번호가 이전과 동일할 경우
        Optional<User> userByUID = userRepository.findByUID(uid);
        if (userByUID.isEmpty()){
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST, null,"등록되지 않은 회원입니다.");
        }

        User user = userByUID.get();
        if (!passwordEncoder.matches(currentPasswd, user.getPasswd())) {
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST, "currentPasswd", "현재 비밀번호가 일치하지 않습니다.");
        } else if (currentPasswd.equals(nextPasswd)) {
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST, "nextPasswd", "비밀번호가 이전과 동일해요.");
        }

        String encryptedPassword = passwordEncoder.encode(nextPasswd);
        //user 정보 업데이트
        userRepository.updatePasswd(encryptedPassword, new Timestamp(new Date().getTime()), uid);
    }


    public void verifyAuthCode(String email, String authCode){
        //미가입자
        Optional<User> userByEmail = userRepository.findByEmail(email);
        if (userByEmail.isEmpty()){
            throw new IllegalStateExceptionWithLocation(HttpStatus.UNAUTHORIZED, "email","가입되지 않은 이메일입니다.");
        }

        //인증코드 불일치
        User user = userByEmail.get();
        if (!user.getAuthCode().equals(authCode)){
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST,"incorrect", "잘못된 인증코드입니다.");
        }

        //만료된 인증코드
        Calendar expiredCalendar = Calendar.getInstance();
        expiredCalendar.setTime(user.getUpdatedAt());
        expiredCalendar.add(Calendar.HOUR, 1);
        Timestamp expiredTime = new Timestamp(expiredCalendar.getTime().getTime());
        Timestamp currentTime = new Timestamp(new Date().getTime());

        if(currentTime.after(expiredTime)){
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST,"expiration", "만료된 인증코드입니다.");
        }

        userRepository.updateIsAuth(true, currentTime, user.getUID());
    }

    public int login(User user){
        Optional<User> userByEmail = userRepository.findByEmail(user.getEmail());
        if(userByEmail.isEmpty())
            throw new IllegalStateExceptionWithLocation(HttpStatus.UNAUTHORIZED, "email","가입되지 않은 이메일입니다.");
        if (!passwordEncoder.matches(user.getPasswd(), userByEmail.get().getPasswd()))
            throw new IllegalStateExceptionWithLocation(HttpStatus.UNAUTHORIZED, "passwd","올바르지 않은 비밀번호입니다.");

        return userByEmail.get().getUID();
    }

    public boolean isAuth(int uid){
        return userRepository.isAuth(uid);
    }

    public MyPageUserResponse.Setting getUserSettingInfo(int userUID) {
        Optional<User> userByUID = userRepository.findByUID(userUID);

        MyPageUserResponse.Setting settingUserInfo = new MyPageUserResponse.Setting(userByUID.get().getImgPath(), userByUID.get().getNickname());
        return settingUserInfo;
    }

    public void changePasswd(int uid, String nextPasswd){
        String encryptedPassword = passwordEncoder.encode(nextPasswd);
        //user 정보 업데이트
        userRepository.updatePasswd(encryptedPassword, new Timestamp(new Date().getTime()), uid);
    }

    public void downLevel(int uid){
        userRepository.updateLevel(uid, new Timestamp(new Date().getTime()));
    }

    public void changeDeviceToken(User user){
        userRepository.updateDeviceToken(user.getUID(), user.getDeviceToken(), new Timestamp(new Date().getTime()));
    }

    public String getDeviceToken(int userUID) {
        Optional<User> userByUID = userRepository.findByUID(userUID);
        return userByUID.get().getDeviceToken();
    }
    public void changeUserSettingInfo(int uid, String nickname, MultipartFile image, Boolean changeImage) {
        //자신 제외하고 닉네임 중복 체크
        Optional<User> userByNickname = userRepository.findByNicknameExceptUser(nickname, uid);
        userByNickname.ifPresent(m -> {
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST,"nickname","중복된 닉네임입니다.");
        });

        String fileName = "u" + uid + ".jpg";//userImage rename

        if( !isNull(image) && !image.isEmpty() ){ // 받은 이미지 저장
            imageService.save(image, fileName);
        }else if(changeImage){ //이미지 없음 + 이미지 변경 원하면 랜덤 이미지 지정
            fileName = "r"+codeGenerator.generateRandomNumber(1)+"jpg";
            // 기존 이미지 삭제
        }
        userRepository.updateNicknameAndImage(uid, nickname, fileName, new Timestamp(new Date().getTime()));
    }

    public MyPageUserResponse.Info getUserMyPageInfo(int uid) {
        Optional<UserMyPageInfo> userMyPageInfo = userRepository.getUserMyPageInfo(uid);

        //cnt, ranking으로 퍼센트 계산하기
        UserMyPageInfo user = userMyPageInfo.get();
        Double percentage = Double.valueOf((double)user.getRanking()/user.getCnt() * 100);
        percentage = Math.round(percentage * 100) / 100.0; //소수점 둘째자리까지

        MyPageUserResponse.Info userInfo = new MyPageUserResponse.Info(user, percentage);
        return userInfo;
    }

    public void deleteExpiredUnauthenticatedUser(){
        userRepository.removeExpiredUnauthenticatedUser();
    }
}
