package trader;

import java.io.StringReader;

import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.provisioning.InMemoryUserDetailsManagerConfigurer;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import trader.common.config.ConfigUtil;
import trader.common.util.EncryptionUtil;
import trader.common.util.IniFile;
import trader.common.util.StringUtil;
import trader.service.security.TraderBrokerAuthenticationEntryPoint;

@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class TraderBrokerSecurityConfiguration extends WebSecurityConfigurerAdapter {
    private static final String ITEM_USERS = "SecurityService/Users";

    private static String REALM = "TRADER BROKER SECURITY REALM";

    //@formatter:off
    @Override
    public void configure(AuthenticationManagerBuilder builder) throws Exception {
        String users = ConfigUtil.getString(ITEM_USERS);
        IniFile usersIni = new IniFile(new StringReader(users));

        InMemoryUserDetailsManagerConfigurer userConfigurer = builder.inMemoryAuthentication();
        for(IniFile.Section section:usersIni.getAllSections()) {
            String username=section.get("username");
            String credential = section.get("credential");
            if ( EncryptionUtil.isEncryptedData(credential)) {
                credential = new String( EncryptionUtil.symmetricDecrypt(credential), StringUtil.UTF8);
            }
            String rolesStr = section.get("roles");
            String[] roles = StringUtil.split(rolesStr, ",|;");
            if ( roles.length==0 ) {
                roles = new String[] {"user"};
            }
            userConfigurer.withUser(username).password(credential).roles(roles);
        }

    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
           .authorizeRequests()
              .antMatchers("/api/**").hasAnyRole("api","user","admin")
              .and()
           //启用 basic authentication
          .httpBasic()
              .realmName(REALM)
              .authenticationEntryPoint(getBasicAuthenticationEntryPoint())
              .and()
           //不创建 session
          .sessionManagement()
          .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
              .and()
          .csrf()
              .disable();
    }

    public BasicAuthenticationEntryPoint getBasicAuthenticationEntryPoint() {
        TraderBrokerAuthenticationEntryPoint entryPoint = new TraderBrokerAuthenticationEntryPoint();
        entryPoint.setRealmName(REALM);
        return entryPoint;
    }

    @Bean
    public AuthenticationManager authenticationManagerBean() throws Exception {
        // ALTHOUGH THIS SEEMS LIKE USELESS CODE,
        // IT'S REQUIRED TO PREVENT SPRING BOOT AUTO-CONFIGURATION
        return super.authenticationManagerBean();
    }

    /*
     * 开放 Options 请求
     */
    @Override
    public void configure(WebSecurity web) throws Exception {
        // TODO Auto-generated method stub
        web.ignoring()
                .antMatchers(HttpMethod.OPTIONS, "/**");
    }

    @SuppressWarnings("deprecation")
    @Bean
    public NoOpPasswordEncoder passwordEncoder() {
        BasicAuthenticationEntryPoint a;
        return (NoOpPasswordEncoder) NoOpPasswordEncoder.getInstance();
    }
}
