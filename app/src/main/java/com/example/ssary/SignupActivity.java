package com.example.ssary;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.*;

import android.view.View;
import android.widget.ImageView;

public class SignupActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore firestore; // Firestore 인스턴스
    private EditText signName, signNickName, signmail, signPW, signPW2, signPhone, signBirth, signBirth2, signBirth3;
    private Button signupButton, pwcheckButton; // 비밀번호 확인 버튼 추가
    private ImageView backButton; // 뒤로가기 버튼 추가

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Firebase Auth 초기화
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        // UI 요소 초기화
        signName = findViewById(R.id.signName);
        signNickName = findViewById(R.id.signNickName);
        signmail = findViewById(R.id.signmail);
        signPW = findViewById(R.id.signPW);
        signPW2 = findViewById(R.id.signPW2);
        signPhone = findViewById(R.id.signPhone);
        signBirth = findViewById(R.id.signBirth);
        signBirth2 = findViewById(R.id.signBirth2);
        signBirth3 = findViewById(R.id.signBirth3);
        signupButton = findViewById(R.id.signupbutton);
        pwcheckButton = findViewById(R.id.pwcheckbutton); // 확인 버튼 초기화
        backButton = findViewById(R.id.back); // 뒤로가기 버튼 초기화

        // 회원가입 버튼 클릭 리스너
        signupButton.setOnClickListener(v -> {
            String email = signmail.getText().toString();
            String password = signPW.getText().toString();
            String confirmPassword = signPW2.getText().toString();
            if (validateInput(email, password, confirmPassword)) {
                createAccount(email, password);
            }
        });

        // 비밀번호 확인 버튼 클릭 리스너 추가
        pwcheckButton.setOnClickListener(v -> {
            String password = signPW.getText().toString();
            String confirmPassword = signPW2.getText().toString();

            if (password.equals(confirmPassword)) {
                Toast.makeText(SignupActivity.this, "비밀번호가 일치합니다.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(SignupActivity.this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
            }
        });

        // 뒤로가기 버튼 클릭 리스너 추가
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 이전 화면으로 돌아가기
                onBackPressed();
            }
        });
    }

    // 입력값 검증
    private boolean validateInput(String email, String password, String confirmPassword) {
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

    // Firebase를 사용한 계정 생성
    private void createAccount(String email, String password) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<com.google.firebase.auth.AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<com.google.firebase.auth.AuthResult> task) {
                        if (task.isSuccessful()) {
                            // 계정 생성 성공
                            FirebaseUser user = auth.getCurrentUser();
                            Toast.makeText(SignupActivity.this, "계정 생성 완료.", Toast.LENGTH_SHORT).show();

                            // 파이어스토어에 저장
                            saveUserInfo(user);

                            // 회원가입 성공 후 로그인 페이지로 이동
                            Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
                            startActivity(intent);
                            finish(); // 현재 SignupActivity 종료
                        } else {
                            // 계정 생성 실패
                            if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                                Toast.makeText(SignupActivity.this, "이미 존재하는 이메일입니다.", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(SignupActivity.this, "계정 생성 실패.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    private void saveUserInfo(FirebaseUser user) {
        if (user != null) {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("name", signName.getText().toString());
            userInfo.put("nickname", signNickName.getText().toString());
            userInfo.put("email", signmail.getText().toString());
            userInfo.put("phone", signPhone.getText().toString());
            userInfo.put("birthDate", signBirth.getText().toString() + "-" +
                    signBirth2.getText().toString() + "-" +
                    signBirth3.getText().toString());

            firestore.collection("users").document(user.getUid())
                    .set(userInfo)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(SignupActivity.this, "사용자 정보 저장 완료.", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(SignupActivity.this, "사용자 정보 저장 실패.", Toast.LENGTH_SHORT).show();
                    });
        }
    }
}
