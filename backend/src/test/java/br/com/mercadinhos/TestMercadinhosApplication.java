package br.com.mercadinhos;

import org.springframework.boot.SpringApplication;

public class TestMercadinhosApplication {

	public static void main(String[] args) {
		SpringApplication.from(MercadinhosApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
