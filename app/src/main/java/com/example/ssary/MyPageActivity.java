package com.example.ssary;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
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
import java.util.TreeMap;

import javax.annotation.Nullable;

public class MyPageActivity extends AppCompatActivity {

    private Spinner categorySpinner;
    private Button addCategoryButton;
    private Button writeButton;
    private Button editCategoryButton; // 카테고리 수정 버튼
    private Button deleteCategoryButton; // 카테고리 삭제 버튼
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

        addCategoryButton = findViewById(R.id.addCategoryButton);
        editCategoryButton = findViewById(R.id.editCategoryButton);
        deleteCategoryButton = findViewById(R.id.deleteCategoryButton);

        // 카테고리와 제목 리스트 초기화
        categoryList = new ArrayList<>();
        titleList = new ArrayList<>();

        // "전체"와 기본 카테고리 추가
        categoryList.add("전체");

        // 어댑터 설정
        categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categoryList);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);

        titleAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titleList);
        titleListView.setAdapter(titleAdapter);

        // Firestore에서 카테고리 로드
        loadCategoriesFromFirestore();

        // ----------------------- 리스너 영역 -----------------------------

        addCategoryButton.setOnClickListener(v -> showAddCategoryDialog());

        // 글 작성 버튼 클릭 리스너
        writeButton = findViewById(R.id.writeButton);
        writeButton.setOnClickListener(v -> {
            Intent intent = new Intent(MyPageActivity.this, MyTextfileActivity.class);
            intent.putStringArrayListExtra("categoryList", (ArrayList<String>) categoryList);
            intent.putExtra("selectedCategory", categorySpinner.getSelectedItem().toString()); // 선택된 카테고리 전달
            startActivityForResult(intent, REQUEST_CODE_WRITE_POST);
        });

        // 카테고리 수정 버튼 클릭 리스너
        editCategoryButton.setOnClickListener(v -> {
            String selectedCategory = categorySpinner.getSelectedItem().toString();
            if (!selectedCategory.equals("전체")) {
                showEditCategoryDialog(selectedCategory);
            }
        });

        // 카테고리 삭제 버튼 클릭 리스너
        deleteCategoryButton.setOnClickListener(v -> {
            String selectedCategory = categorySpinner.getSelectedItem().toString();
            if (!selectedCategory.equals("전체")) {
                showDeleteCategoryDialog(selectedCategory);
            }
        });

        // 카테고리 선택 시 Firestore에서 글 제목 로드
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCategory = categoryList.get(position);

                // "전체"가 선택되었을 때 글 작성 버튼 숨기기
                if (selectedCategory.equals("전체")) {
                    editCategoryButton.setVisibility(View.GONE);
                    deleteCategoryButton.setVisibility(View.GONE);
                    writeButton.setVisibility(View.GONE); // 글 작성 버튼 숨기기
                    loadAllTitlesFromFirestore(); // 모든 글 로드
                } else {
                    // 특정 카테고리가 선택되면 글 작성 버튼 보이기
                    editCategoryButton.setVisibility(View.VISIBLE);
                    deleteCategoryButton.setVisibility(View.VISIBLE);
                    writeButton.setVisibility(View.VISIBLE); // 글 작성 버튼 보이기
                    loadTitlesFromFirestore(selectedCategory); // 특정 카테고리의 글만 로드
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 글 목록 클릭 리스너
        titleListView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedTitleWithCategory = titleList.get(position);
            String selectedCategory = categorySpinner.getSelectedItem().toString();

            // "전체" 모드일 때는 아무 작업도 하지 않음
            if (selectedCategory.equals("전체")) {
                return;
            }

            // 특정 카테고리가 선택된 경우 제목과 카테고리를 사용해 Firestore에서 내용을 조회
            loadPostContent(selectedTitleWithCategory, selectedCategory);
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
                    // "전체"와 "자소서"를 초기화 후 유지
                    categoryList.clear();
                    categoryList.add("전체");

                    // Firestore에서 카테고리 추가
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

    // Firestore에서 모든 글 불러오기 (카테고리별로 그룹화 및 정렬해서 표시)
    private void loadAllTitlesFromFirestore() {
        db.collection("posts")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        // 카테고리별로 그룹화하기 위한 맵 생성
                        Map<String, List<String>> categorizedTitles = new TreeMap<>(); // 자동으로 카테고리 이름 순으로 정렬

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String category = document.getString("category");
                            String title = document.getString("title");

                            if (title != null && category != null) {
                                // 해당 카테고리에 속하는 리스트에 글 제목 추가
                                categorizedTitles.putIfAbsent(category, new ArrayList<>());
                                categorizedTitles.get(category).add(title);
                            }
                        }

                        // 카테고리별로 정렬된 제목 리스트 초기화
                        titleList.clear();
                        for (Map.Entry<String, List<String>> entry : categorizedTitles.entrySet()) {
                            String category = entry.getKey();
                            List<String> titles = entry.getValue();

                            // 각 카테고리에 속하는 글 제목을 "[카테고리] 글제목" 형태로 추가
                            for (String title : titles) {
                                titleList.add("[" + category + "] " + title);
                            }
                        }
                    } else {
                        Toast.makeText(this, "글 목록을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                    }
                    titleAdapter.notifyDataSetChanged();
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

    // Firestore에서 글 내용을 불러와 다이얼로그로 표시
    private void loadPostContent(String title, String category) {
        db.collection("posts")
                .whereEqualTo("title", title)
                .whereEqualTo("category", category)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentSnapshot document = task.getResult().getDocuments().get(0);
                        String content = document.getString("content");
                        String documentId = document.getId(); // 삭제 시 필요한 문서 ID

                        AlertDialog dialog = new AlertDialog.Builder(this)
                                .setTitle(title)
                                .setMessage(content != null ? content : "내용이 없습니다.")
                                .setPositiveButton("수정", (d, which) -> {
                                    // 수정 페이지로 이동
                                    loadPostForEditing(title, category);
                                })
                                .setNeutralButton("닫기", (d, which) -> d.dismiss())
                                .create();

                        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "삭제", (d, which) -> {
                            // 삭제 기능 실행
                            deletePost(documentId, title, category);
                        });

                        // 삭제 버튼 색상 빨간색으로 설정
                        dialog.setOnShowListener(d -> {
                            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                        });

                        dialog.show();
                    } else {
                        Toast.makeText(this, "글 내용을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Firestore에서 제목을 선택하여 글 수정 페이지로 이동
    private void loadPostForEditing(String title, String category) {
        db.collection("posts")
                .whereEqualTo("title", title)
                .whereEqualTo("category", category)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentSnapshot document = task.getResult().getDocuments().get(0);
                        String content = document.getString("content");
                        String documentId = document.getId();

                        Intent intent = new Intent(MyPageActivity.this, MyTextfileActivity.class);
                        intent.putExtra("title", title);
                        intent.putExtra("content", content);
                        intent.putExtra("category", category);
                        intent.putExtra("documentId", documentId);
                        intent.putStringArrayListExtra("categoryList", (ArrayList<String>) categoryList);
                        startActivity(intent);
                    } else {
                        Toast.makeText(this, "글을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // 카테고리 추가 다이얼로그(팝업창) 표시
    private void showAddCategoryDialog() {
        final EditText input = new EditText(this);
        input.setHint("새 카테고리 입력");

        // Firestore에서 카테고리 목록 가져오기
        List<String> firestoreCategoryList = new ArrayList<>();

        // Firestore에서 카테고리 불러와서 가로 정렬 레이아웃에 추가
        LinearLayout horizontalLayout = new LinearLayout(this);
        horizontalLayout.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView scrollView = new HorizontalScrollView(this);
        scrollView.addView(horizontalLayout);

        // Firestore에서 카테고리 가져와서 가로 레이아웃에 동적 추가
        db.collection("categories")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    firestoreCategoryList.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String category = document.getString("name");
                        if (category != null && !firestoreCategoryList.contains(category)) {
                            firestoreCategoryList.add(category);

                            // 각 카테고리를 텍스트뷰로 추가
                            TextView categoryTextView = new TextView(this);
                            categoryTextView.setText(category);
                            categoryTextView.setPadding(16, 8, 16, 8);
                            horizontalLayout.addView(categoryTextView);
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "카테고리를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show());

        // 다이얼로그 상단에 "카테고리 목록" 라벨 추가
        TextView categoryLabel = new TextView(this);
        categoryLabel.setText("카테고리 목록");
        categoryLabel.setPadding(0, 16, 0, 8);

        // 레이아웃 설정: EditText, TextView, ScrollView를 포함하는 세로 레이아웃 생성
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(input);          // 새 카테고리 입력 필드
        layout.addView(categoryLabel);   // "카테고리 목록" 라벨 추가
        layout.addView(scrollView);      // 카테고리 목록 가로 정렬

        // 다이얼로그 빌더 생성
        new AlertDialog.Builder(this)
                .setTitle("카테고리 추가")
                .setView(layout)
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

    private void showEditCategoryDialog(String selectedCategory) {
        final EditText input = new EditText(this);
        input.setText(selectedCategory);

        new AlertDialog.Builder(this)
                .setTitle("카테고리 이름 수정")
                .setView(input)
                .setPositiveButton("수정", (dialog, which) -> {
                    String newCategoryName = input.getText().toString().trim();
                    if (!newCategoryName.isEmpty() && !newCategoryName.equals(selectedCategory)) {
                        updateCategoryInFirestore(selectedCategory, newCategoryName);
                    }
                })
                .setNegativeButton("취소", (dialog, which) -> dialog.cancel())
                .show();
    }

    private void updateCategoryInFirestore(String oldCategory, String newCategory) {
        db.collection("categories")
                .whereEqualTo("name", oldCategory)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        db.collection("categories").document(document.getId())
                                .update("name", newCategory)
                                .addOnSuccessListener(aVoid -> {
                                    categoryList.set(categoryList.indexOf(oldCategory), newCategory);
                                    categoryAdapter.notifyDataSetChanged();
                                    updatePostsCategory(oldCategory, newCategory);
                                    Toast.makeText(this, "카테고리가 수정되었습니다.", Toast.LENGTH_SHORT).show();
                                });
                    }
                });
    }

    private void updatePostsCategory(String oldCategory, String newCategory) {
        db.collection("posts")
                .whereEqualTo("category", oldCategory)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        db.collection("posts").document(document.getId())
                                .update("category", newCategory);
                    }
                });
    }

    private void showDeleteCategoryDialog(String selectedCategory) {
        new AlertDialog.Builder(this)
                .setTitle("카테고리 삭제")
                .setMessage("정말 삭제하시겠습니까? 해당 카테고리 내, 모든 글이 삭제됩니다.")
                .setPositiveButton("삭제", (dialog, which) -> deleteCategoryFromFirestore(selectedCategory))
                .setNegativeButton("취소", (dialog, which) -> dialog.cancel())
                .show();
    }

    private void deleteCategoryFromFirestore(String category) {
        db.collection("categories")
                .whereEqualTo("name", category)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        db.collection("categories").document(document.getId()).delete();
                    }
                    deletePostsInCategory(category);
                    categoryList.remove(category);
                    categoryAdapter.notifyDataSetChanged();
                    Toast.makeText(this, "카테고리가 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                });
    }

    private void deletePostsInCategory(String category) {
        db.collection("posts")
                .whereEqualTo("category", category)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        db.collection("posts").document(document.getId()).delete();
                    }
                });
    }

    // Firestore에서 글 삭제
    private void deletePost(String documentId, String title, String category) {
        db.collection("posts").document(documentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "글이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                    loadTitlesFromFirestore(category); // 삭제 후 목록 갱신
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "글 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
    }
}
