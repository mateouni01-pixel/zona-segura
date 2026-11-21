package com.zonasegura.zona_segura_api;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

/** Un JWT todavía vigente no conserva permisos revocados ni habilita una cuenta suspendida. */
public class CuentaVigenteFilter extends OncePerRequestFilter {
    private final AuthService auth;
    public CuentaVigenteFilter(AuthService auth){this.auth=auth;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication instanceof JwtAuthenticationToken token&&authentication.isAuthenticated()){
            try{
                var profile=auth.profile(token.getToken().getSubject());
                var roles=profile.roles().stream().map(role->new SimpleGrantedAuthority("ROLE_"+role)).toList();
                SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token.getToken(),roles,token.getName()));
            }catch(ResponseStatusException exception){
                SecurityContextHolder.clearContext();response.setStatus(exception.getStatusCode().value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"detalle\":\"La cuenta está inactiva o su acceso cambió. Ingresa nuevamente.\"}");return;
            }
        }
        chain.doFilter(request,response);
    }
}
