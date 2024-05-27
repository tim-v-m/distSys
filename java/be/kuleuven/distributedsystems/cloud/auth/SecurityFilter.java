package be.kuleuven.distributedsystems.cloud.auth;

import be.kuleuven.distributedsystems.cloud.entities.User;
import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Autowired
    boolean isProduction;

    @Autowired
    String projectId;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // TODO: (level 1) decode Identity Token and assign correct email and role
        // TODO: (level 2) verify Identity Token

        String payload = request.getHeader("Authorization");
        if (payload != null) {
            String userId = payload.replace("Bearer ", "");

            User user = null;

            //if(isProduction) {
            //    try {

            //    Algorithm algorithm = Algorithm.RSA256(rsaPublicKey, rsaPrivateKey);
            //
            //    JWTVerifier verifier = JWT.require(algorithm)
            //        .withIssuer("auth0")
            //        .build();
            //
            //    decodedJWT = verifier.verify(payload);
            //
            //     var email = jwt.getClaim("email");
            //     var role = jwt.getClaim("role");
            //     user = new User(email.asString(), role.asString());


            //} catch (Exception e) {
            //    filterChain.doFilter(request, response);
            //}
            //}
            //else {
            //decode jwt
            try {
                DecodedJWT jwt = JWT.decode(userId);
                var email = jwt.getClaim("email");
                var role = jwt.getClaim("role");
                user = new User(email.asString(), role.asString());
            } catch (JWTVerificationException e) {
                filterChain.doFilter(request, response);
            }
            //}

            //block users from manager api
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if(( path.startsWith("/api/getAllBookings") ||  path.startsWith("/api/getBestCustomers") )
                    && !user.isManager())
                filterChain.doFilter(request, response);

            SecurityContext context = SecurityContextHolder.getContext();
            context.setAuthentication(new FirebaseAuthentication(user));
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api");
    }

    private static class FirebaseAuthentication implements Authentication {
        private final User user;

        FirebaseAuthentication(User user) {
            this.user = user;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            if (user.isManager()) {
                return List.of(new SimpleGrantedAuthority("manager"));
            } else {
                return new ArrayList<>();
            }
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getDetails() {
            return null;
        }

        @Override
        public User getPrincipal() {
            return this.user;
        }

        @Override
        public boolean isAuthenticated() {
            return true;
        }

        @Override
        public void setAuthenticated(boolean b) throws IllegalArgumentException {

        }

        @Override
        public String getName() {
            return null;
        }
    }
}

