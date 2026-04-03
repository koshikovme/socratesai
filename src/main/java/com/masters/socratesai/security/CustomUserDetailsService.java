package com.masters.socratesai.security;

import com.masters.socratesai.user.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public SecurityUserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(SecurityUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}