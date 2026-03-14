package lappick.config;

import javax.sql.DataSource;

import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final DataSource dataSource;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    PathRequest.toStaticResources().atCommonLocations() 
                ).permitAll()
                .requestMatchers(
                    "/resources/**", 
                    "/static/**", 
                    "/upload/**"
                ).permitAll()
                .requestMatchers(
                    "/",
                    "/error",
                    "/auth/login",
                    "/auth/register/**",
                    "/auth/find-id",
                    "/auth/find-pw",
                    "/auth/userIdCheck",
                    "/goods/goodsFullList",
                    "/goods/detail/**",
                    "/banner/**"
                ).permitAll()

                .requestMatchers(
                    "/cart/**",
                    "/purchases/**",
                    "/member/**",
                    "/qna/**",
                    "/review/**"
                ).hasAuthority("ROLE_MEMBER")
                
                .requestMatchers(
                    "/admin/**",
                    "/employee/**"
                ).hasAuthority("ROLE_EMPLOYEE")
                
                .anyRequest().authenticated()
            )
            .formLogin(login -> login
                .loginPage("/auth/login")
                .loginProcessingUrl("/auth/login")
                .usernameParameter("id")
                .passwordParameter("pw")
                .defaultSuccessUrl("/", true)
                .failureUrl("/auth/login?error=true")
                .permitAll()
            )
            .rememberMe(remember -> remember
                .rememberMeParameter("remember-me")
                .tokenRepository(persistentTokenRepository())
                .tokenValiditySeconds(86400 * 14)
                .userDetailsService(userDetailsService)
            )
            .logout(logout -> logout
                    .logoutUrl("/auth/logout")
                    .logoutSuccessHandler(customLogoutSuccessHandler())
                    .invalidateHttpSession(true) // 로그아웃 시 세션을 반드시 파괴
                    .deleteCookies("JSESSIONID", "remember-me")
                );

        return http.build();
    }
    
    @Bean
    public PersistentTokenRepository persistentTokenRepository() {
        JdbcTokenRepositoryImpl repo = new JdbcTokenRepositoryImpl();
        repo.setDataSource(dataSource);
        return repo;
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public LogoutSuccessHandler customLogoutSuccessHandler() {
        return new LogoutSuccessHandler() {
            @Override
            public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
                
                String targetUrl = "/"; // 기본 리다이렉트 URL
                
                // 1. /auth/logout?withdraw=success 파라미터가 있는지 확인
                String withdrawParam = request.getParameter("withdraw");
                if ("success".equals(withdrawParam)) {
                    // 2. 메인 페이지로 보낼 파라미터 추가
                    targetUrl = "/?message=withdrawSuccess";
                }
                
                // 3. 최종 목적지로 리다이렉트
                // (세션과 쿠키는 이미 .invalidateHttpSession(true)와 .deleteCookies()에 의해 처리됨)
                response.sendRedirect(request.getContextPath() + targetUrl);
            }
        };
    }
}
