package com.example.api.controller;

import com.example.api.dto.*;
import com.example.api.model.*;
import com.example.api.repository.*;
import com.example.api.service.*;
import com.example.api.utils.*;

import java.util.Date;
import java.util.Map;

import javax.xml.crypto.Data;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties.Jwt;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import com.auth0.jwt.interfaces.DecodedJWT;

@Controller // This means that this class is a Controller
@RequestMapping(path = "/api") // This means URL's start with /demo (after Application path)
public class UserController {
    @Autowired // This means to get the bean called userRepository
    // Which is auto-generated by Spring, we will use it to handle the data
    private userService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    /**
     * *
     * 
     * @param user
     * @return userDTO
     */
    public UserDTO convertToDto(User user) {
        UserDTO userDTO = new UserDTO();
        userDTO.setUid(user.getUid());
        userDTO.setNickname(user.getNickname());
        userDTO.setName(user.getUsername());
        userDTO.setEmail(user.getEmail());
        userDTO.setPhone(user.getPhone());
        return userDTO;
    }

    @PostMapping(value = "/register", consumes = { "multipart/form-data" })
    public ResponseEntity<Result> register(User user) {
        Result result = userService.register(user);
        return new ResponseEntity<>(result, HttpStatus.OK);

    }

    @PostMapping("/login")
    public ResponseEntity<Result> login(@RequestParam String emailOrPhone, @RequestParam String password) {
        // Result result = userService.login(emailOrPhone, password);
        // return new ResponseEntity<>(result, HttpStatus.OK);

        Result<UserDTO> result = new Result<UserDTO>();

        String hashedPassword = passwordEncoder.encode(password);

        User user = userRepository.findByEmail(emailOrPhone)
                .orElse((User) userRepository.findByPhone(emailOrPhone).orElse(null));

        if (user != null && passwordEncoder.matches(password, user.getPassword())) { // Encrypted passwords should
                                                                                     // be used in practical
                                                                                     // applications
            UserDTO userDTO = convertToDto(user);
            result.setResultSuccess("Login success", userDTO);
            HttpHeaders headers = new HttpHeaders();
            Date exp = new Date();
            exp.setTime(exp.getTime() + 1000 * 60 * 60 * 24 * 7); // 7 days
            Map<String, Object> data = Map.of("user", userDTO);
            headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + JWTManager.createToken(exp, data));
            headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.AUTHORIZATION);

            return new ResponseEntity<>(result, headers, HttpStatus.OK);
        } else {
            result.setResultFailed("Login failed");
            return new ResponseEntity<>(result, HttpStatus.UNAUTHORIZED);
        }
    }

    @GetMapping("/users/me")
    public ResponseEntity<Result> getUserByAuthToken(@RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
        Result<UserDTO> result = new Result<UserDTO>();
        if (token == null || token.isEmpty() || !token.contains("Bearer ")) {
            result.setResultFailed("Invalid token!");
            return new ResponseEntity<>(result, HttpStatus.UNAUTHORIZED);
        }

        token = token.replace("Bearer ", "");

        UserDTO userDTO = JWTManager.getDataFromToken(token, "user", UserDTO.class);

        if (userDTO == null) {
            result.setResultFailed("Invalid token!");
            return new ResponseEntity<>(result, HttpStatus.UNAUTHORIZED);
        }

        UserDTO updatedUserDTO = convertToDto(userRepository.findById(userDTO.getUid()).orElse(null));

        if (updatedUserDTO == null) {
            result.setResultFailed("User not found!");
            return new ResponseEntity<>(result, HttpStatus.NOT_FOUND);
        }

        if (!updatedUserDTO.equals(userDTO)) {
            result.setResultSuccess("Get user success", updatedUserDTO);

            HttpHeaders headers = new HttpHeaders();
            Date exp = new Date();
            exp.setTime(exp.getTime() + 1000 * 60 * 60 * 24 * 7); // 7 days
            Map<String, Object> data = Map.of("user", updatedUserDTO);
            headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + JWTManager.createToken(exp, data));
            headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.AUTHORIZATION);

            return new ResponseEntity<>(result, headers, HttpStatus.OK);
        }

        result.setResultSuccess("Get user success", userDTO);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Result> forgotPassword(@RequestParam String emailOrPhone) {
        User user = userRepository.findByEmail(emailOrPhone)
                .orElse((User) userRepository.findByPhone(emailOrPhone).orElse(null));

        if (user != null) {
            String token = JWTManager.createToken(new Date(System.currentTimeMillis() + 1000 * 60 * 15), // 15 minutes
                    Map.of("uid", user.getUid()));

            String resetPasswordLink = "%BASE_URL%/reset-password/" + token + "/";

            try {
                EmailManager.sendEmail(emailOrPhone,
                        "Reset password for " + user.getUsername(),
                        "Hello " + user.getUsername() + "!" + "<br><br>" +
                                " Please follow this link to reset your password: " + "<br>" +
                                " <a href=\"" + resetPasswordLink + "\">" + resetPasswordLink + "</a>" + "<br><br>" +
                                " The link will expire in 15 minutes." + "<br><br>" +
                                " Do NOT share this link with anyone." + "<br><br>" +
                                " If you did not request a password reset, please ignore this email.");
            } catch (Exception e) {
                e.printStackTrace();

                Result result = new Result();
                result.setResultFailed("Failed to send email");
                return new ResponseEntity<>(result, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        Result result = new Result();
        result.setResultSuccess("Reset password link has been sent to the email if it exists");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @PostMapping("/validate-reset-password-token")
    public ResponseEntity<Result> validateResetPasswordToken(@RequestParam String resetPasswordToken) {
        Result<UserDTO> result = new Result<UserDTO>();
        if (resetPasswordToken == null || resetPasswordToken.isEmpty()) {
            result.setResultFailed("Invalid token!");
            return new ResponseEntity<>(result, HttpStatus.UNAUTHORIZED);
        }

        int uid = JWTManager.getDataFromToken(resetPasswordToken, "uid", Integer.class);

        if (uid == 0) {
            result.setResultFailed("Invalid token!");
            return new ResponseEntity<>(result, HttpStatus.UNAUTHORIZED);
        }

        UserDTO userDTO = convertToDto(userRepository.findById(uid).orElse(null));

        if (userDTO == null) {
            result.setResultFailed("User not found!");
            return new ResponseEntity<>(result, HttpStatus.NOT_FOUND);
        }

        result.setResultSuccess("Token is valid", userDTO);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Result> resetPassword(@RequestParam String resetPasswordToken, @RequestParam String password,
            @RequestParam String confirmPassword) {
        Result result = new Result();

        if (!password.equals(confirmPassword)) {
            result.setResultFailed("Passwords do not match");
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }

        int uid = JWTManager.getDataFromToken(resetPasswordToken, "uid", Integer.class);

        if (uid == 0) {
            result.setResultFailed("Invalid token");
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findById(uid).orElse(null);

        if (user == null) {
            result.setResultFailed("User not found");
            return new ResponseEntity<>(result, HttpStatus.NOT_FOUND);
        }

        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);

        result.setResultSuccess("Password reset successfully");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@RequestParam Integer uid, @RequestParam String token,
            @RequestParam String newPassword) {
        userService.changePassword(uid, token, newPassword);
        return ResponseEntity.ok("Password changed successfully");
    }
}