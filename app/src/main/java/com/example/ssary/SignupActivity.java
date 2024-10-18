package com.example.ssary;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

public class SignupActivity extends AppCompatActivity {

    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        db = FirebaseFirestore.getInstance(); // Firestore 초기화

        // UI 요소 참조
        EditText passwordEditText = findViewById(R.id.signPW);
        EditText confirmPasswordEditText = findViewById(R.id.signPW2);
        EditText nicknameEditText = findViewById(R.id.signNickName);
        EditText nameEditText = findViewById(R.id.signName);
        EditText phoneEditText = findViewById(R.id.signPhone);
        EditText mailText = findViewById(R.id.signmail);
        Button confirmButton = findViewById(R.id.pwcheckbutton);
        Button signupButton = findViewById(R.id.signupbutton);

        // 비밀번호 확인 버튼 클릭 이벤트
        confirmButton.setOnClickListener(v -> {
            String password = passwordEditText.getText().toString();
            String confirmPassword = confirmPasswordEditText.getText().toString();

            if (password.equals(confirmPassword)) {
                Toast.makeText(SignupActivity.this, "비밀번호가 일치합니다.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(SignupActivity.this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
            }
        });

        // 회원가입 버튼 클릭 이벤트
        signupButton.setOnClickListener(v -> {
            String email = mailText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();
            String name = nameEditText.getText().toString();
            String nickname = nicknameEditText.getText().toString();
            String phone = phoneEditText.getText().toString();

            // 입력 데이터 검증
            if (name.isEmpty() || phone.isEmpty() || password.isEmpty() || email.isEmpty()) {
                Toast.makeText(SignupActivity.this, "모든 필드를 입력하세요!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Firestore에 사용자 데이터 저장
            User user = new User(name, nickname, phone, password, email);
            db.collection("Users").document(email).set(user)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(SignupActivity.this, "회원가입이 완료되었습니다!", Toast.LENGTH_SHORT).show();
                            // 로그인 화면으로 이동
                            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
                            finish();
                        } else {
                            Toast.makeText(SignupActivity.this, "회원가입 실패: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    // Firestore에 저장할 User 클래스
    public static class User {
        public String name, nickname, phone, password, email;

        public User(String name, String nickname, String phone, String password, String email) {
            this.name = name;
            this.nickname = nickname;
            this.phone = phone;
            this.password = password; // 저장된 비밀번호 (해싱하지 않고 저장하는 경우)
            this.email = email;
        }
    }
}
