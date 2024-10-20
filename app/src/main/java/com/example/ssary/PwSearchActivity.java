package com.example.ssary;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

public class PwSearchActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private EditText editMail, editName, editBirth, editPhone;
    private Button searchPWButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pw_search); // layout 파일 이름으로 변경

        // FirebaseAuth 및 Firestore 인스턴스 초기화
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // UI 요소 초기화
        editMail = findViewById(R.id.editmail);
        editName = findViewById(R.id.editname);
        editBirth = findViewById(R.id.editbirth);
        editPhone = findViewById(R.id.editphone);
        searchPWButton = findViewById(R.id.search_PW);

        // 생년월일 입력 포맷팅 예를 들어, DB 포맷팅이 YYYY-MM-DD 이기 때문에 입력 포맷을 1998-03-07로 변경한다.
        editBirth.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Nothing to do here
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Nothing to do here
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;

                String input = s.toString().replaceAll("[^\\d]", ""); // 숫자만 남기기
                isFormatting = true;

                StringBuilder formatted = new StringBuilder();
                if (input.length() > 0) {
                    formatted.append(input.substring(0, Math.min(input.length(), 4))); // 연도
                }
                if (input.length() >= 5) {
                    formatted.append("-").append(input.substring(4, Math.min(input.length(), 6))); // 월
                }
                if (input.length() >= 7) {
                    formatted.append("-").append(input.substring(6, Math.min(input.length(), 8))); // 일
                }

                editBirth.setText(formatted.toString());
                editBirth.setSelection(formatted.length()); // 커서 위치 조정
                isFormatting = false;

//                Log.d("IdSearchActivity", editBirth.getText().toString());
            }

        });
        // 전화번호 입력 포맷팅 예를 들어, DB 포맷팅이 01012345678으로 되어있는데, 입력 포맷을 010-1234-5678로 변경할 수 있다.
        // 나중에 DB 포맷팅을 변경하면 좋을 것 같다.
//        editPhone.addTextChangedListener(new TextWatcher() {
//            private boolean isFormatting;
//
//            @Override
//            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
//                // Nothing to do here
//            }
//
//            @Override
//            public void onTextChanged(CharSequence s, int start, int before, int count) {
//                // Nothing to do here
//            }
//
//            @Override
//            public void afterTextChanged(Editable s) {
//                if (isFormatting) return;
//
//                String input = s.toString().replaceAll("[^\\d]", ""); // 숫자만 남기기
//                isFormatting = true;
//
//                StringBuilder formatted = new StringBuilder();
//                if (input.length() > 0) {
//                    formatted.append(input.substring(0, Math.min(input.length(), 3))); // 010
//                }
//                if (input.length() >= 4) {
//                    formatted.append("-").append(input.substring(3, Math.min(input.length(), 7))); // 번호2
//                }
//                if (input.length() >= 8) {
//                    formatted.append("-").append(input.substring(7, Math.min(input.length(), 11))); // 번호3
//                }
//
//                editPhone.setText(formatted.toString());
//                editPhone.setSelection(formatted.length()); // 커서 위치 조정
//                isFormatting = false;
//
////                Log.d("IdSearchActivity", editBirth.getText().toString());
//            }
//
//        });

        // 비밀번호 찾기 버튼 클릭 리스너
        searchPWButton.setOnClickListener(v -> {
            String email = editMail.getText().toString().trim();
            String name = editName.getText().toString().trim();
            String birth = editBirth.getText().toString().trim();
            String phone = editPhone.getText().toString().trim();

            // 입력값 검증
            if (TextUtils.isEmpty(email)) {
                Toast.makeText(this, "이메일을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            } else if (TextUtils.isEmpty(name)) {
                Toast.makeText(this, "이름을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            } else if (TextUtils.isEmpty(birth)) {
                Toast.makeText(this, "생년월일을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            } else if (TextUtils.isEmpty(phone)) {
                Toast.makeText(this, "핸드폰번호를 입력해 주세요.", Toast.LENGTH_SHORT).show();
            } else {
                checkUserDetails(email, name, birth, phone);
            }
        });
    }

    private void checkUserDetails(String email, String name, String birth, String phone) {
        // Firestore에서 사용자 정보 조회
        db.collection("users")
                .whereEqualTo("email", email)
                .whereEqualTo("name", name)
                .whereEqualTo("birthDate", birth) // 생년월일 필드 이름에 맞추어 수정
                .whereEqualTo("phone", phone)
                .limit(1) // 조건에 맞는 사용자 한 명만 검색
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        // 사용자 정보가 존재하면 이메일을 보여줌
                        DocumentSnapshot document = task.getResult().getDocuments().get(0);
                        String foundEmail = document.getString("email");
                        Toast.makeText(this, "비밀번호 재설정 이메일을 보냈습니다.", Toast.LENGTH_SHORT).show();
                        // 비밀번호 재설정 이메일 전송
                        auth.sendPasswordResetEmail(foundEmail);
                    } else {
                        // 사용자 정보가 없는 경우
                        Toast.makeText(this, "해당 정보로 등록된 사용자를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w("Firestore", "사용자 정보 검색 실패", e);
                    Toast.makeText(this, "오류가 발생했습니다. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show();
                });
    }

}