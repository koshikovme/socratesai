package com.masters.socratesai;

import com.masters.socratesai.mentor.feedback.gemini.GeminiProperties;
import com.masters.socratesai.mentor.feedback.openai.OpenAiProperties;
import com.masters.socratesai.mentor.policy.MentorPolicyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({OpenAiProperties.class, GeminiProperties.class, MentorPolicyProperties.class})
public class SocratesaiApplication {

	public static void main(String[] args) {
		SpringApplication.run(SocratesaiApplication.class, args);
	}

}
