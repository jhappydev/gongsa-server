package study.gongsa.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import study.gongsa.domain.Category;
import study.gongsa.dto.CategoryResponse;
import study.gongsa.repository.CategoryRepository;
import study.gongsa.support.exception.IllegalStateExceptionWithLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor

public class CategoryService {
    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getAllCategory(){
        List<Category> categories = categoryRepository.findAll();
        List<CategoryResponse> categoryResponses = new ArrayList<CategoryResponse>();

        for (Category category : categories){
            categoryResponses.add(new CategoryResponse(category));
        }

        return categoryResponses;
    }

    public List<Category> getStudyGroupCategory(int groupUID){
        return categoryRepository.findByGroupUID(groupUID);
    }

    public void checkValidCategoryUID(int[] groupCategories) {
        for(int categoryUID : groupCategories){
            Optional<Category> categoryByUID = categoryRepository.findByUID(categoryUID);
            if(categoryByUID.isEmpty()) throw new IllegalStateExceptionWithLocation(HttpStatus.BAD_REQUEST,"categoryUID","존재하지 않는 카테고리입니다.");
        }
    }
}
