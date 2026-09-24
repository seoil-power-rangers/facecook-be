package com.facecook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 서버 시작점. {@code @SpringBootApplication}이 이 패키지({@code com.facecook}) 아래의
 * {@code @Component}·{@code @Service}·{@code @Controller}·{@code @Configuration}을 찾아
 * 객체(빈)로 만들고, 생성자 파라미터에 필요한 빈을 넣어 서로 연결한다.
 *
 * <p>그래서 코드에서 {@code new AuthService(...)}를 직접 하는 곳이 없다. 각 클래스는
 * 필요한 것을 {@code private final} 필드로 선언하고 Lombok
 * {@code @RequiredArgsConstructor}가 만든 생성자로 받는다(생성자 주입).</p>
 */
@SpringBootApplication
public class FacecookBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(FacecookBeApplication.class, args);
	}

}
