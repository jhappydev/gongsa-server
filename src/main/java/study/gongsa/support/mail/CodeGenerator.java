package study.gongsa.support.mail;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
@Getter
@Setter
public class CodeGenerator {
    public String generateRandomNumber(int numberLength) {
        Random random = new Random(System.currentTimeMillis());

        int range = (int)Math.pow(10,numberLength);
        int trim = (int)Math.pow(10, numberLength-1);
        int result = random.nextInt(range)+trim;

        if(result>range){
            result = result - trim;
        }

        return String.valueOf(result);
    }

    public String generateRandomString(int numberLength) {
        StringBuffer result = new StringBuffer();
        Random rnd =new Random();

        for(int i=0; i<numberLength; i++){
            int rIndex = rnd.nextInt(3);
            switch(rIndex) {
                case 0: //a-z
                    result.append((char)((int) (rnd.nextInt(26)) + 97));
                    break;
                case 1: //A-Z
                    result.append((char)((int) (rnd.nextInt(26)) + 65));
                    break;
                case 2: //0-9
                    result.append(rnd.nextInt(10));
                    break;
            }
        }
        return String.valueOf(result);
    }
}