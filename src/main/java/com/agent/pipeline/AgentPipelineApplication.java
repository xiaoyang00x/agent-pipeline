package com.agent.pipeline;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.agent.pipeline.infrastructure.persistence.mapper")
public class AgentPipelineApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgentPipelineApplication.class, args);
	}

}
