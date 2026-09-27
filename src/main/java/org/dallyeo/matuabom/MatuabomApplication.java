package org.dallyeo.matuabom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;

@SpringBootApplication(exclude = {
    MongoRepositoriesAutoConfiguration.class
})
public class MatuabomApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatuabomApplication.class, args);
    }

}
