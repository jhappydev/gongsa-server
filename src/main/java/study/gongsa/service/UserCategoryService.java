package study.gongsa.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import study.gongsa.domain.User;
import study.gongsa.domain.UserCategory;
import study.gongsa.dto.UserCategoryDTO;
import study.gongsa.repository.UserCategoryRepository;
import study.gongsa.support.exception.IllegalStateExceptionWithLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserCategoryService {
    private final UserCategoryRepository userCategoryRepository;

    public void save(UserCategory userCategory) {
        userCategoryRepository.save(userCategory);
    }

    public void remove(int userUID){
        List<UserCategory> userCategoryByUserUID = userCategoryRepository.findByUserUID(userUID);
        // 이미 카테고리가 있으면 삭제하고 다시 추가
        for(int i=0; i<userCategoryByUserUID.size(); i++)
            userCategoryRepository.remove(userCategoryByUserUID.get(i).getUID());
    }

    public List<UserCategoryDTO> findAll(int userUID){
        List<UserCategory> userCategories = userCategoryRepository.findByUserUID(userUID);
        List<UserCategoryDTO> userCategoryDTOs = new ArrayList<UserCategoryDTO>();

        for(UserCategory userCategory: userCategories){
            UserCategoryDTO userCategoryDTO = new UserCategoryDTO();
            userCategoryDTO.setUserCategoryUID(userCategory.getUID());
            userCategoryDTO.setCategoryUID(userCategory.getCategoryUID());
            userCategoryDTOs.add(userCategoryDTO);
        }

        return userCategoryDTOs;
    }
}
