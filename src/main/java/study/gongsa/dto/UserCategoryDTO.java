package study.gongsa.dto;

import lombok.*;

import java.util.Date;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCategoryDTO {
    private int userCategoryUID;
    private int categoryUID;
}
