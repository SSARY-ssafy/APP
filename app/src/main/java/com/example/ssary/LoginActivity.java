package com.example.ssary;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private EditText loginEmail, loginPassword;
    private Button loginButton, signupButton, searchIdButton, searchPwButton, jobButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Firebase Auth 초기화
        auth = FirebaseAuth.getInstance();

        // UI 요소 초기화
        loginEmail = findViewById(R.id.loginEmail);
        loginPassword = findViewById(R.id.loginPassword);
        loginButton = findViewById(R.id.loginButton);
        signupButton = findViewById(R.id.signin); // 회원가입 버튼
        searchIdButton = findViewById(R.id.id);
        searchPwButton = findViewById(R.id.pw);
        jobButton = findViewById(R.id.job_button);

        // 로그인 버튼 클릭 리스너
        loginButton.setOnClickListener(v -> {
            String email = loginEmail.getText().toString();
            String password = loginPassword.getText().toString();
            if (validateInput(email, password)) {
                loginUser(email, password);
            }
        });

        // 회원가입 버튼 클릭 리스너
        signupButton.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
            startActivity(intent);
        });

        // 아이디 찾기 버튼
        searchIdButton.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, IdSearchActivity.class);
            startActivity(intent);
        });

        // 비밀번호 찾기 버튼
        searchPwButton.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, PwSearchActivity.class);
            startActivity(intent);
        });

        jobButton.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, JobActivity.class);
            startActivity(intent);
        });
    }

    // 입력값 검증
    private boolean validateInput(String email, String password) {
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "이메일을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            Toast.makeText(this, "비밀번호를 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    // Firebase를 사용한 로그인
    private void loginUser(String email, String password) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // 로그인 성공
                            Toast.makeText(LoginActivity.this, "로그인 성공!", Toast.LENGTH_SHORT).show();
                        } else {
                            // 로그인 실패
                            Toast.makeText(LoginActivity.this, "로그인 실패. 이메일 또는 비밀번호를 확인하세요.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
}
