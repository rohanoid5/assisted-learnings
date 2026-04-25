package com.taskforge.taskforge.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
@Order(1)
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        long start = System.currentTimeMillis();
        System.out.printf("[Filter] --> %s %s%n", req.getMethod(), req.getRequestURI());
        
        chain.doFilter(request, response);
        
        long duration = System.currentTimeMillis() - start;
        System.out.printf("[Filter] <-- %s %s (%dms)%n", req.getMethod(), req.getRequestURI(), duration);
    }
}
