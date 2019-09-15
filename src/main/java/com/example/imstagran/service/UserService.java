package com.example.imstagran.service;

import com.example.imstagran.domain.Role;
import com.example.imstagran.domain.User;
import com.example.imstagran.repos.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService implements UserDetailsService {
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private MailSender mailSender;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepo.findByUsername(username);
        if (user == null)
            throw new UsernameNotFoundException("User not found");

        return user;
    }

    public boolean addUser(User user) {
        User userFromDb = userRepo.findByUsername(user.getUsername());

        if (userFromDb != null) {
            return false;
        }

        user.setActive(true);
        user.setRoles(Collections.singleton(Role.USER));
        user.setActivationCode(UUID.randomUUID().toString());
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        userRepo.save(user);
        sendMessage(user);


        return true;
    }

    public boolean activateUser(String code) {
        User user = userRepo.findByActivationCode(code);
        if (user == null) {
            return false;
        }

        user.setActivationCode(null);
        userRepo.save(user);
        return true;
    }

    public List<User> findAll() {
        return userRepo.findAll();
    }

    public void saveUser(User user, String username, Map<String, String> form) {
        user.setUsername(username);
        Set<String> roles = Arrays.stream(Role.values())
                .map(r -> r.name())
                .collect(Collectors.toSet());

        user.getRoles().clear();

        for (String key : form.keySet()) {
            if (roles.contains(key)) {
                user.getRoles().add(Role.valueOf(key));
            }
        }
        userRepo.save(user);
    }

    public void updateProfile(User user, String password, String email) {
        String userEmail = user.getEmail();
        boolean isEmailChanged = email != null && !email.equals(userEmail) ||
                userEmail != null && !userEmail.equals(email);
        if (isEmailChanged) {
            user.setEmail(email);
            if (!StringUtils.isEmpty(email)) {
                user.setActivationCode(UUID.randomUUID().toString());
            }
        }
        if (!StringUtils.isEmpty(password)) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        userRepo.save(user);
        if (isEmailChanged)
            sendMessage(user);
    }

    private void sendMessage(User user) {
        if (!StringUtils.isEmpty(user.getEmail())) {
            String msg = String.format("Hello, %s! \n" +
                            "Welcome to Imstagran. Please, visit next link: http://localhost:8080/activate/%s",
                    user.getUsername(), user.getActivationCode());
            mailSender.send(user.getEmail(), "Activation Code", msg);
        }
    }

    public void addSubscription(User user, User subscription) {
        user.getSubscriptions().add(subscription);
        subscription.getSubscribers().add(user);
        userRepo.save(user);
    }

    public void removeSubscription(User user, User subscription) {
        user.getSubscriptions().remove(subscription);
        subscription.getSubscribers().remove(user);
        userRepo.save(user);
    }
}