package com.example.ssary;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class MyPageActivity extends AppCompatActivity {

    private Spinner categorySpinner;
    private Button addCategoryButton;
    private Button writeButton;
    private ListView titleListView;

    // 카테고리 리스트와 제목 리스트
    private List<String> categoryList;
    private List<String> titleList;
    private ArrayAdapter<String> categoryAdapter;
    private ArrayAdapter<String> titleAdapter;

    // Firestore 인스턴스
    private FirebaseFirestore db;

    private static final int REQUEST_CODE_WRITE_POST = 1; // 글 작성 요청 코드

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_page);

        // Firestore 인스턴스 초기화
        db = FirebaseFirestore.getInstance();

        // 카테고리 스피너 초기화
        categorySpinner = findViewById(R.id.categorySpinner);
        titleListView = findViewById(R.id.titleListView);

        // 카테고리와 제목 리스트 초기화
        categoryList = new ArrayList<>();
        titleList = new ArrayList<>();

        // 기본 카테고리 추가
        categoryList.add("자소서");

        // 어댑터 설정
        categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categoryList);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);

        titleAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titleList);
        titleListView.setAdapter(titleAdapter);

        // Firestore에서 카테고리 로드
        loadCategoriesFromFirestore();

        // 카테고리 추가 버튼 초기화
        addCategoryButton = findViewById(R.id.addCategoryButton);
        addCategoryButton.setOnClickListener(v -> showAddCategoryDialog());

        // 글 작성 버튼 초기화
        writeButton = findViewById(R.id.writeButton);
        writeButton.setOnClickListener(v -> {
            Intent intent = new Intent(MyPageActivity.this, MyTextfileActivity.class);
            intent.putStringArrayListExtra("categoryList", (ArrayList<String>) categoryList);
            intent.putExtra("selectedCategory", categorySpinner.getSelectedItem().toString()); // 선택된 카테고리 전달
            startActivityForResult(intent, REQUEST_CODE_WRITE_POST);
        });

        // 카테고리 선택 시 Firestore에서 글 제목 로드
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCategory = categoryList.get(position);
                loadTitlesFromFirestore(selectedCategory);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 제목 선택 시 글 내용 로드
        titleListView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedTitle = titleList.get(position);
            String selectedCategory = categorySpinner.getSelectedItem().toString();
            loadPostForEditing(selectedTitle, selectedCategory);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_WRITE_POST && resultCode == RESULT_OK && data != null) {
            // 글 작성 후 돌아오면 선택한 카테고리의 글 목록 다시 로드
            String updatedCategory = data.getStringExtra("updatedCategory");
            if (updatedCategory != null) {
                loadTitlesFromFirestore(updatedCategory); // 전달받은 카테고리로 글 목록 다시 로드
            }
        }
    }

    // Firestore에서 카테고리 불러오기
    private void loadCategoriesFromFirestore() {
        db.collection("categories")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    categoryList.clear();
                    categoryList.add("자소서"); // 기본 카테고리 유지
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String category = document.getString("name");
                        if (category != null && !categoryList.contains(category)) {
                            categoryList.add(category);
                        }
                    }
                    categoryAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    Log.w("Firestore", "Failed to load categories", e);
                    Toast.makeText(this, "카테고리를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
    }

    // Firestore에서 선택한 카테고리의 글 제목 불러오기
    private void loadTitlesFromFirestore(String category) {
        db.collection("posts")
                .whereEqualTo("category", category)
                .get()
                .addOnCompleteListener(task -> {
                    titleList.clear();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String title = document.getString("title");
                            if (title != null) {
                                titleList.add(title);
                            }
                        }
                    } else {
                        Toast.makeText(this, "글 목록을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                    }
                    titleAdapter.notifyDataSetChanged();
                });
    }

    // Firestore에 새 카테고리 추가
    private void addCategoryToFirestore(String newCategory) {
        Map<String, Object> category = new HashMap<>();
        category.put("name", newCategory);

        db.collection("categories").add(category)
                .addOnSuccessListener(documentReference -> {
                    categoryList.add(newCategory);
                    categoryAdapter.notifyDataSetChanged();
                    Toast.makeText(MyPageActivity.this, "카테고리가 추가되었습니다.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(MyPageActivity.this, "카테고리 추가에 실패했습니다.", Toast.LENGTH_SHORT).show());
    }

    // Firestore에서 제목을 선택하여 글 내용 로드 및 수정 페이지로 이동
    private void loadPostForEditing(String title, String category) {
        db.collection("posts")
                .whereEqualTo("title", title)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        String documentId = task.getResult().getDocuments().get(0).getId();
                        String content = task.getResult().getDocuments().get(0).getString("content");

                        Intent intent = new Intent(MyPageActivity.this, MyTextfileActivity.class);
                        intent.putExtra("title", title);
                        intent.putExtra("content", content);
                        intent.putExtra("category", category); // 선택된 카테고리 전달
                        intent.putExtra("documentId", documentId);
                        intent.putStringArrayListExtra("categoryList", (ArrayList<String>) categoryList); // 카테고리 리스트 전달
                        startActivity(intent);
                    } else {
                        Toast.makeText(MyPageActivity.this, "글을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // 카테고리 추가 다이얼로그(팝업창) 표시
    private void showAddCategoryDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("카테고리 입력");

        new AlertDialog.Builder(this)
                .setTitle("카테고리 추가")
                .setView(input)
                .setPositiveButton("추가", (dialog, which) -> {
                    String newCategory = input.getText().toString().trim();
                    if (!newCategory.isEmpty()) {
                        addCategoryToFirestore(newCategory);
                    } else {
                        Toast.makeText(this, "카테고리를 입력해 주세요.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("취소", (dialog, which) -> dialog.cancel())
                .show();
    }
}
