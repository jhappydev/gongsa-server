package study.gongsa.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import study.gongsa.domain.Answer;
import study.gongsa.domain.AnswerInfo;
import study.gongsa.domain.GroupMember;
import study.gongsa.domain.Question;
import study.gongsa.repository.AnswerRepository;
import study.gongsa.repository.QuestionRepository;
import study.gongsa.support.exception.IllegalStateExceptionWithLocation;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnswerService {
    private final AnswerRepository answerRepository;
    private final GroupMemberService groupMemberService;
    private final QuestionService questionService;

    public List<AnswerInfo> findAnswerByQuestionUID(int questionUID){
        return answerRepository.findAnswer(questionUID);
    }

    public Answer findAnswer(int answerUID){
        Optional<Answer> answer = answerRepository.findOne(answerUID);
        if(answer.isEmpty()){
            throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST, "answerUID","존재하지 않는 답변입니다.");
        }
        return answer.get();
    }

    public void makeAnswer(int userUID, int questionUID, String content) {
        Question question = questionService.findOne(questionUID);
        int groupUID = question.getGroupUID();
        GroupMember groupMember = groupMemberService.findOne(groupUID, userUID);

        Answer answer = Answer.builder()
                .userUID(userUID)
                .questionUID(questionUID)
                .groupUID(groupUID)
                .groupMemberUID(groupMember.getUID())
                .answer(content)
                .build();
        answerRepository.save(answer);
    }

    public int getQuestionUIDByAnswerUID(int userUID, int answerUID) {
        Answer answer = findAnswer(answerUID);
        if(answer.getUserUID() != userUID){
            throw new IllegalStateExceptionWithLocation(HttpStatus.FORBIDDEN, "userUID","수정 권한이 없습니다.");
        }

        // 질문글 찾기
        Question question = questionService.findOne(answer.getQuestionUID());
        return question.getUID();
    }

    public void deleteUserAnswer(List<Integer> questionUIDs, int userUID){
        answerRepository.deleteUserAnswer(questionUIDs, userUID);
    }

    public void updateAnswer(int answerUID, String content) {
        answerRepository.update(answerUID, content);
    }

    public void deleteAnswer(int answerUID, int userUID){
        Answer answer = findAnswer(answerUID);
        if(answer.getUserUID() != userUID){
            throw new IllegalStateExceptionWithLocation(HttpStatus.FORBIDDEN, "userUID","삭제 권한이 없습니다.");
        }

        answerRepository.remove(answerUID);
    }
}
