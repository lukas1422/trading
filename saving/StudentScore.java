package saving;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

public class StudentScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    String student;

    @Column(name = "SCORE")
    double score;

    public StudentScore(String name, double s) {
        student = name;
        score = s;
    }

    public StudentScore() {
    }

    String getStudent() {
        return student;
    }

    double getScore() {
        return score;
    }

}
