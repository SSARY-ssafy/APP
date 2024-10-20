package com.example.ssary;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

public class PwSearchActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private EditText editMail, editName, editBirth, editPhone;
    private Button searchPWButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pw_search); // layout 파일 이름으로 변경

        // FirebaseAuth 인스턴스 초기화
        auth = FirebaseAuth.getInstance();

        // UI 요소 초기화
        editMail = findViewById(R.id.editmail);
        editName = findViewById(R.id.editname);
        editBirth = findViewById(R.id.editbirth);
        editPhone = findViewById(R.id.editphone);
        searchPWButton = findViewById(R.id.search_PW);

        // 비밀번호 찾기 버튼 클릭 리스너
        searchPWButton.setOnClickListener(v -> {
            String email = editMail.getText().toString().trim();
            String name = editName.getText().toString().trim();
            String birth = editBirth.getText().toString().trim();
            String phone = editPhone.getText().toString().trim();

            // 이메일 입력 검증
            if (TextUtils.isEmpty(email)) {
                Toast.makeText(this, "이메일을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            } else if (TextUtils.isEmpty(name)) {
                Toast.makeText(this, "이름을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            } else if (TextUtils.isEmpty(birth)) {
                Toast.makeText(this, "생년월일을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            } else if (TextUtils.isEmpty(phone)) {
                Toast.makeText(this, "핸드폰번호를 입력해 주세요.", Toast.LENGTH_SHORT).show();
            } else {
                sendPasswordResetEmail(email);
            }
        });
    }

    private void sendPasswordResetEmail(String email) {
        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "비밀번호 재설정 이메일을 보냈습니다.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "이메일 전송 실패. 이메일을 확인해 주세요.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
