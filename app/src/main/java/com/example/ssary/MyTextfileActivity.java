package com.example.ssary;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MyTextfileActivity extends AppCompatActivity {

    private Spinner categorySpinner; // 카테고리 선택 스피너
    private EditText postTitleEditText; // 글 제목 입력 필드
    private EditText postContentEditText; // 글 내용 입력 필드
    private Button submitPostButton; // 글 저장 버튼

    // Firestore 인스턴스
    private FirebaseFirestore db;

    // 글 수정 모드 여부를 나타내는 플래그
    private boolean isEditMode = false;
    private String documentId; // 수정할 글의 Firestore 문서 ID

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_textfile);

        // Firestore 인스턴스 초기화
        db = FirebaseFirestore.getInstance();

        // UI 요소 초기화
        categorySpinner = findViewById(R.id.categorySpinner);
        postTitleEditText = findViewById(R.id.postTitleEditText);
        postContentEditText = findViewById(R.id.postContentEditText);
        submitPostButton = findViewById(R.id.submitPostButton);

        // 전달받은 카테고리 리스트와 선택된 카테고리 받기
        ArrayList<String> categoryList = getIntent().getStringArrayListExtra("categoryList");
        String selectedCategory = getIntent().getStringExtra("selectedCategory");
        if (categoryList == null || categoryList.isEmpty()) {
            categoryList = new ArrayList<>();
        }
        if (!categoryList.contains("자소서")) {
            categoryList.add(0, "자소서");
        }

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categoryList);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);

        // 전달된 제목, 내용, 카테고리 및 수정 모드 설정
        String title = getIntent().getStringExtra("title");
        String content = getIntent().getStringExtra("content");
        String category = getIntent().getStringExtra("category");
        documentId = getIntent().getStringExtra("documentId"); // Firestore 문서 ID 받기

        // 글 수정 모드일 때 제목과 내용을 설정
        if (title != null && content != null) {
            postTitleEditText.setText(title);
            postContentEditText.setText(content);
            if (category != null) {
                int categoryIndex = categoryList.indexOf(category);
                if (categoryIndex != -1) {
                    categorySpinner.setSelection(categoryIndex); // 전달받은 카테고리 선택
                }
            }
            isEditMode = true;
            submitPostButton.setText("글 수정"); // 버튼 텍스트를 "글 수정"으로 변경
        } else {
            // 새 글 작성 시 기본 카테고리 선택
            int defaultCategoryIndex = categoryList.indexOf("자소서");
            if (defaultCategoryIndex != -1) {
                categorySpinner.setSelection(defaultCategoryIndex);
            }
        }

        // 선택된 카테고리 기본 선택
        if (selectedCategory != null) {
            int categoryIndex = categoryList.indexOf(selectedCategory);
            if (categoryIndex != -1) {
                categorySpinner.setSelection(categoryIndex);
            }
        }

        // 글 저장 버튼 클릭 리스너 추가
        submitPostButton.setOnClickListener(v -> {
            String selectedCategoryInSpinner = categorySpinner.getSelectedItem().toString();
            String postTitle = postTitleEditText.getText().toString().trim();
            String postContent = postContentEditText.getText().toString().trim();

            if (postTitle.isEmpty() || postContent.isEmpty()) {
                Toast.makeText(MyTextfileActivity.this, "제목과 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            } else {
                if (isEditMode) {
                    updatePostInFirestore(documentId, selectedCategoryInSpinner, postTitle, postContent);
                } else {
                    savePostToFirestore(selectedCategoryInSpinner, postTitle, postContent);
                }
            }
        });
    }

    // Firestore에 글 저장 메서드
    private void savePostToFirestore(String category, String title, String content) {
        Map<String, Object> post = new HashMap<>();
        post.put("category", category);
        post.put("title", title);
        post.put("content", content);

        db.collection("posts").add(post)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(MyTextfileActivity.this, "글이 성공적으로 저장되었습니다.", Toast.LENGTH_SHORT).show();
                    Intent resultIntent = new Intent(); // 작성한 카테고리 정보 전달
                    resultIntent.putExtra("updatedCategory", category);
                    setResult(RESULT_OK, resultIntent);
                    finish(); // 현재 Activity 종료
                })
                .addOnFailureListener(e -> Toast.makeText(MyTextfileActivity.this, "글 저장에 실패했습니다.", Toast.LENGTH_SHORT).show());
    }

    // Firestore에 글 수정 메서드
    private void updatePostInFirestore(String documentId, String category, String title, String content) {
        Map<String, Object> updatedPost = new HashMap<>();
        updatedPost.put("category", category);
        updatedPost.put("title", title);
        updatedPost.put("content", content);

        db.collection("posts").document(documentId)
                .update(updatedPost)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(MyTextfileActivity.this, "글이 성공적으로 수정되었습니다.", Toast.LENGTH_SHORT).show();
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("updatedCategory", category);
                    setResult(RESULT_OK, resultIntent);
                    finish(); // 현재 Activity 종료
                })
                .addOnFailureListener(e -> Toast.makeText(MyTextfileActivity.this, "글 수정에 실패했습니다.", Toast.LENGTH_SHORT).show());
    }
}
