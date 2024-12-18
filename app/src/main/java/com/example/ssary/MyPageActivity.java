package com.example.ssary;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
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
    private Button changeColorButton; // 카테고리 색상 변경 버튼
    private ListView titleListView;

    // 카테고리 리스트와 제목 리스트
    private List<String> categoryList;
    private List<String> titleList;
    private ArrayAdapter<String> categoryAdapter;
    private ArrayAdapter<String> titleAdapter;

    // 검색을 위한 텍스트 리스트와 아이콘
    private EditText searchEditText;
    private ImageView searchIcon;

    // Firestore 인스턴스
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private static final int REQUEST_CODE_WRITE_POST = 1; // 글 작성 요청 코드
    private static final int REQUEST_CODE_READ_UPDATE_DELETE = 2; // 읽기, 수정, 삭제 요청 코드

    // 색상 맵
    private Map<String, Integer> categoryColorMap;

    private final List<String> fixedColors = Arrays.asList(
            "#FF5733", // Red
            "#33FF57", // Green
            "#3357FF", // Blue
            "#FF33FF", // Magenta
            "#33FFFF", // Cyan
            "#FFFF33", // Yellow
            "#FFA500", // Orange
            "#800080", // Purple
            "#808080", // Gray
            "#000000"  // Black
    );

    private Map<String, Integer> categoryColorCache = new HashMap<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_page);

        // Firestore 인스턴스 초기화
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // 카테고리 스피너 초기화
        categorySpinner = findViewById(R.id.categorySpinner);
        titleListView = findViewById(R.id.titleListView);
        changeColorButton = findViewById(R.id.changeColorButton);

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

        // 색상 데이터 미리 로드
        loadCategoryColors();

//        titleAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titleList);
//        titleListView.setAdapter(titleAdapter);

        // 타이틀에 색상을 추가한 어댑터
        titleAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, titleList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);

                String fullTitle = getItem(position);
                if (fullTitle != null && fullTitle.startsWith("[")) {
                    int endIndex = fullTitle.indexOf("]");
                    if (endIndex > 0) {
                        String category = fullTitle.substring(1, endIndex);

                        // 캐싱된 색상 사용
                        int color = categoryColorCache.getOrDefault(category, Color.BLACK);

                        // 스팬 적용
                        SpannableString styledText = new SpannableString(fullTitle);
                        styledText.setSpan(new ForegroundColorSpan(color), 0, endIndex + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        textView.setText(styledText);
                    } else {
                        textView.setText(fullTitle);
                        textView.setTextColor(Color.BLACK);
                    }
                } else {
                    textView.setText(fullTitle);
                    textView.setTextColor(Color.BLACK);
                }

                return view;
            }
        };



        titleListView.setAdapter(titleAdapter);



        // 검색을 위한 변수들 초기화
        searchEditText = findViewById(R.id.searchEditText);
        searchIcon = findViewById(R.id.searchIcon);
        setupOutsideTouchListener();

        // Firestore에서 카테고리 로드
        loadCategoriesFromFirestore();


        // 필요한 카테고리별 색상을 추가 -> 추후에 스토어에 저장하는 방식으로 변경 필요 할 듯
        categoryColorMap = new HashMap<>();
        categoryColorMap.put("전체", Color.BLUE);
        categoryColorMap.put("자소서", Color.YELLOW);
        categoryColorMap.put("이름수정 테스트1", Color.GREEN);
        categoryColorMap.put("파일업로드 테스트", Color.MAGENTA);
        categoryColorMap.put("이력서 전략", Color.GRAY);
        categoryColorMap.put("코딩테스트", Color.CYAN);

        // Firestore 기본값 보장
        ensureCategoryColorsExist();


        // ----------------------- 리스너 영역 -----------------------------

        addCategoryButton.setOnClickListener(v -> showAddCategoryDialog());

        // 글 작성 버튼 클릭 리스너
        writeButton = findViewById(R.id.writeButton);
//        writeButton.setOnClickListener(v -> {
//            String selectedCategory = categorySpinner.getSelectedItem().toString();
//            Intent intentToTextfile = new Intent(MyPageActivity.this, MyNewTextActivity.class);
//            intentToTextfile.putExtra("category", selectedCategory);
//            startActivity(intentToTextfile);
//        });

        // 수정된 글 작성 버튼 클릭 리스너
        writeButton.setOnClickListener(v -> {
            String selectedCategory;

            // Spinner에서 선택된 값 가져오기
            if (categorySpinner.getSelectedItem() != null) {
                selectedCategory = categorySpinner.getSelectedItem().toString();
            } else {
                selectedCategory = "전체"; // 기본값 설정
            }

            // "전체"도 새 글을 작성할 수 있도록 허용
            Intent intentToTextfile = new Intent(MyPageActivity.this, MyNewTextActivity.class);
            intentToTextfile.putExtra("category", selectedCategory);
            startActivity(intentToTextfile);
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

        changeColorButton.setOnClickListener(v -> {
            String selectedCategory = categorySpinner.getSelectedItem().toString();
            if (!selectedCategory.equals("전체")) {
                showFixedColorPickerDialog(selectedCategory);
            } else {
                Toast.makeText(MyPageActivity.this, "카테고리를 선택하세요.", Toast.LENGTH_SHORT).show();
            }
        });

        // 카테고리 선택 시 Firestore에서 글 제목 로드
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCategory = categoryList.get(position);

                // "전체"가 선택되었을 때 글 작성 버튼 숨기기
                if (selectedCategory.equals("전체")) {
                    addCategoryButton.setVisibility(View.VISIBLE);
                    editCategoryButton.setVisibility(View.GONE);
                    deleteCategoryButton.setVisibility(View.GONE);

                    changeColorButton.setVisibility(View.GONE);

                    writeButton.setVisibility(View.VISIBLE); // 글 작성 버튼 보이기
                    loadAllTitlesFromFirestore(); // 모든 글 로드
                } else {
                    // 특정 카테고리가 선택되면 글 작성 버튼 보이기
                    addCategoryButton.setVisibility(View.VISIBLE);
                    editCategoryButton.setVisibility(View.VISIBLE);
                    deleteCategoryButton.setVisibility(View.VISIBLE);

                    changeColorButton.setVisibility(View.VISIBLE);

                    writeButton.setVisibility(View.VISIBLE); // 글 작성 버튼 보이기
                    loadTitlesFromFirestore(selectedCategory); // 특정 카테고리의 글만 로드
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        titleListView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedTitle = titleList.get(position);
            String selectedCategory = categorySpinner.getSelectedItem().toString();

            if (selectedCategory.equals("전체")) {
                return;
            }

            // Firestore에서 선택된 제목과 카테고리에 해당하는 문서 ID 가져오기
            db.collection("posts")
                    .whereEqualTo("title", selectedTitle)
                    .whereEqualTo("category", selectedCategory)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && !task.getResult().isEmpty()) {
                            String documentId = task.getResult().getDocuments().get(0).getId();

                            // MyExistTextActivity로 이동하며 선택된 카테고리, 제목, 문서 ID를 전달
                            Intent intentToReadUpdateDelete = new Intent(MyPageActivity.this, MyExistTextActivity.class);
                            intentToReadUpdateDelete.putExtra("title", selectedTitle);
                            intentToReadUpdateDelete.putExtra("category", selectedCategory);
                            intentToReadUpdateDelete.putExtra("documentId", documentId);  // 문서 ID 전달
                            startActivity(intentToReadUpdateDelete);
                        } else {
                            Toast.makeText(MyPageActivity.this, "해당 글을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                        }
                    });
        });



        searchIcon.setOnClickListener(v -> {
            if (searchEditText.getVisibility() == View.GONE) {
                // 검색 입력 필드 보이기
                searchEditText.setVisibility(View.VISIBLE);
                searchEditText.requestFocus(); // 포커스 주기
                // 키보드 열기
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
            } else {
                // 검색 입력 필드 숨기기
                searchEditText.setVisibility(View.GONE);
                // 키보드 닫기
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
            }
        });

        // 검색 입력 필드에 텍스트 변화 감지 리스너 추가
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 검색어에 따라 제목 리스트 필터링
                String query = s.toString().toLowerCase();
                filterTitles(query);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });


        // 외부 터치 리스너 설정
        setupOutsideTouchListener();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_WRITE_POST && resultCode == RESULT_OK && data != null) {
            String updatedCategory = data.getStringExtra("updatedCategory");
            if (updatedCategory != null) {
                loadTitlesFromFirestore(updatedCategory); // 작성 후 글 목록 로드
            }
        } else if (requestCode == REQUEST_CODE_READ_UPDATE_DELETE && resultCode == RESULT_OK && data != null) {
            String deletedCategory = data.getStringExtra("deletedCategory");
            if (deletedCategory != null) {
                // 글 목록을 다시 로드하여 삭제된 글을 반영
                loadTitlesFromFirestore(deletedCategory);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 현재 선택된 카테고리를 다시 로드하여 최신 상태로 업데이트
        String selectedCategory = categorySpinner.getSelectedItem().toString();
        if (selectedCategory.equals("전체")) {
            loadAllTitlesFromFirestore();
        } else {
            loadTitlesFromFirestore(selectedCategory);
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

    // 정렬 없이 불러오는 방식
//    private void loadAllTitlesFromFirestore() {
//        db.collection("posts")
//                .get()
//                .addOnCompleteListener(task -> {
//                    if (task.isSuccessful() && task.getResult() != null) {
//                        titleList.clear(); // 기존 리스트 초기화
//
//                        for (QueryDocumentSnapshot document : task.getResult()) {
//                            String category = document.getString("category");
//                            String title = document.getString("title");
//
//                            if (category != null && title != null) {
//                                // 단순히 제목에 카테고리를 추가한 문자열 생성
//                                titleList.add("[" + category + "] " + title);
//                            }
//                        }
//                    } else {
//                        Toast.makeText(this, "글 목록을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
//                    }
//                    titleAdapter.notifyDataSetChanged(); // 리스트 업데이트
//                });
//    }

    // Firestore에서 선택된 카테고리의 글 제목을 다시 불러오기 (카테고리 목록 재구성)
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
                    titleAdapter.notifyDataSetChanged(); // 목록 즉시 갱신
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

                        Intent intent = new Intent(MyPageActivity.this, MyNewTextActivity.class);
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

    // 외부 터치 리스너
    private void setupOutsideTouchListener() {
        findViewById(R.id.myPageLayout).setOnTouchListener((v, event) -> {
            if (searchEditText.getVisibility() == View.VISIBLE) {
                searchEditText.setVisibility(View.GONE);
                // 키보드 닫기
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
            }
            return false;
        });
    }

    // 제목 필터링 메서드
    private void filterTitles(String query) {
        titleList.clear();

        // 선택된 카테고리 가져오기
        String selectedCategory = categorySpinner.getSelectedItem().toString();

        if (query.isEmpty()) {
            // 카테고리가 "전체"일 경우 모든 제목을 로드
            if (selectedCategory.equals("전체")) {
                loadAllTitlesFromFirestore(); // 모든 제목을 가져오는 메소드 호출
            } else {
                loadTitlesFromFirestore(selectedCategory); // 선택된 카테고리의 글 로드
            }
        } else {
            // 카테고리가 "전체"일 경우 모든 제목을 가져옴
            if (selectedCategory.equals("전체")) {
                db.collection("posts")
                        .get()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    String title = document.getString("title");
                                    if (title != null && title.toLowerCase().contains(query.toLowerCase())) {
                                        titleList.add(title);
                                    }
                                }
                                titleAdapter.notifyDataSetChanged();
                            }
                        });
            } else {
                // 특정 카테고리의 제목을 가져와 필터링
                db.collection("posts")
                        .whereEqualTo("category", selectedCategory)
                        .get()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    String title = document.getString("title");
                                    if (title != null && title.toLowerCase().contains(query.toLowerCase())) {
                                        titleList.add(title);
                                    }
                                }
                                titleAdapter.notifyDataSetChanged();
                            }
                        });
            }
        }
    }


    private void showFixedColorPickerDialog(String category) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("색상을 선택하세요");

        ArrayAdapter<String> colorAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, fixedColors) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);
                textView.setBackgroundColor(Color.parseColor(fixedColors.get(position)));
                textView.setText("");
                textView.setHeight(100);
                return view;
            }
        };

        builder.setAdapter(colorAdapter, (dialog, which) -> {
            String selectedColor = fixedColors.get(which);
            updateCategoryColors(category, selectedColor);
            loadCategoryColors();
        });

        builder.setNegativeButton("취소", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void updateCategoryColors(String category, String color) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "사용자 정보를 확인할 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users").document(user.getUid())
                .update("categoryColors." + category, color)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "카테고리 색상이 업데이트되었습니다.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "카테고리 색상 업데이트에 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
    }

    private void ensureCategoryColorsExist() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "사용자 정보를 확인할 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = user.getUid();

        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists() || !documentSnapshot.contains("categoryColors")) {
                        // 기본 카테고리 색상 값 추가
                        Map<String, String> defaultColors = new HashMap<>();
                        defaultColors.put("전체", "#000000"); // 검정
                        defaultColors.put("자소서", "#FF5733"); // 빨강

                        // 사용자 문서 생성 또는 업데이트
                        db.collection("users").document(userId)
                                .update("categoryColors", defaultColors)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d("Firestore", "기본 카테고리 색상 추가 완료");
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "기본 카테고리 색상 추가 실패", Toast.LENGTH_SHORT).show();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "사용자 데이터를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadCategoryColors() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "사용자 정보를 확인할 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Map<String, String> categoryColors = (Map<String, String>) documentSnapshot.get("categoryColors");
                        if (categoryColors != null) {
                            // 캐싱
                            categoryColorCache.clear();
                            for (Map.Entry<String, String> entry : categoryColors.entrySet()) {
                                categoryColorCache.put(entry.getKey(), Color.parseColor(entry.getValue()));
                            }
                            // 어댑터에 알림
                            titleAdapter.notifyDataSetChanged();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "카테고리 색상을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
    }


}
