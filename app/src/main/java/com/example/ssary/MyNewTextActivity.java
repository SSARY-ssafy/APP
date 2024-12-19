package com.example.ssary;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextWatcher;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MyNewTextActivity extends AppCompatActivity {

    private EditText titleEditText, contentEditText;
    private LinearLayout uploadedFileContainer;
    private ImageView boldButton, italicButton, underlineButton, strikethroughButton, imageButton;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
  
    private final List<Uri> fileUris = new ArrayList<>();
    private final List<String> fileNames = new ArrayList<>();
    private Spinner categorySpinner;
    private ArrayList<String> categoryList;
    private ArrayAdapter<String> categoryAdapter;

    private boolean isBold = false, isItalic = false, isUnderline = false, isStrikethrough = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_new_text);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        titleEditText = findViewById(R.id.titleEditText);
        contentEditText = findViewById(R.id.contentEditText);
        uploadedFileContainer = findViewById(R.id.uploadedFileContainer);
        boldButton = findViewById(R.id.boldButton);
        italicButton = findViewById(R.id.italicButton);
        underlineButton = findViewById(R.id.underlineButton);
        strikethroughButton = findViewById(R.id.strikethroughButton);
        ImageView uploadFileButton = findViewById(R.id.uploadFileButton);
        imageButton = findViewById(R.id.imageButton);
        Button submitPostButton = findViewById(R.id.submitPostButton);

        categorySpinner = findViewById(R.id.categorySpinner);
        categoryList = new ArrayList<>();
        categoryList.add("카테고리 선택");
        loadCategoriesFromDB();

        categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categoryList);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);

        String selectedCategory = getIntent().getStringExtra("category");
        if (selectedCategory != null && !selectedCategory.isEmpty()) {
            categorySpinner.setSelection(categoryList.indexOf(selectedCategory));
        }

        uploadFileButton.setOnClickListener(v -> selectFiles());

        submitPostButton.setOnClickListener(v -> {
            String postTitle = titleEditText.getText().toString().trim();
            String postContent = contentEditText.getText().toString().trim();
            String category = categorySpinner.getSelectedItem().toString();

            if (postTitle.isEmpty() || postContent.isEmpty()) {
                Toast.makeText(MyNewTextActivity.this, "제목과 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            } else {
                if (!fileUris.isEmpty()) {
                    uploadFileToStorage(postTitle, postContent, category);
                } else {
                    savePostToDB(category, postTitle, postContent, null);
                }
            }
        });

        setupButtonListeners();
        setupTextWatcher();

        contentEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyCurrentStyleToInput();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    // DB로 부터 카테고리 목록을 가져오는 메서드
    private void loadCategoriesFromDB() {
        db.collection("categories")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (com.google.firebase.firestore.QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String category = document.getString("name");
                        if (category != null && !categoryList.contains(category)) {
                            categoryList.add(category);
                        }
                    }
                    categoryAdapter.notifyDataSetChanged();
                  
                    String selectedCategory = getIntent().getStringExtra("category");
                    if (selectedCategory != null && !selectedCategory.isEmpty()) {
                        int position = categoryList.indexOf(selectedCategory);
                        if (position >= 0) {
                            categorySpinner.setSelection(position);
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "카테고리를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show());
    }

    // 게시글 내용을 데이터베이스에 저장
    private void savePostToDB(String category, String title, String content, List<Map<String, String>> fileData) {
        Map<String, Object> post = new HashMap<>();
        String htmlContent = convertToHtmlStyledContent(content);

        post.put("category", category);
        post.put("title", title);
        post.put("content", htmlContent);
        post.put("files", fileData);

        db.collection("posts").add(post)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(MyNewTextActivity.this, "글이 성공적으로 저장되었습니다.", Toast.LENGTH_SHORT).show();
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("updatedCategory", category);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(MyNewTextActivity.this, "글 저장에 실패했습니다.", Toast.LENGTH_SHORT).show());
    }

    // 업로드된 파일을 스토리지에 저장
    private void uploadFileToStorage(String title, String content, String category) {
        List<Map<String, String>> fileData = new ArrayList<>();
        int totalFiles = fileUris.size();
        final int[] completedFiles = {0};

        for (int i = 0; i < totalFiles; i++) {
            Uri fileUri = fileUris.get(i);
            String fileName = fileNames.get(i);
            String fileExtension = getFileExtension(fileUri);

            String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName;
            StorageReference fileRef = storage.getReference().child("uploads/" + uniqueFileName);

            fileRef.putFile(fileUri)
                    .addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        Map<String, String> fileInfo = new HashMap<>();
                        fileInfo.put("fileName", fileName);
                        fileInfo.put("fileExtension", fileExtension != null ? fileExtension : "unknown");
                        fileInfo.put("fileUrl", uri.toString());
                        fileData.add(fileInfo);

                        completedFiles[0]++;
                        if(completedFiles[0] == totalFiles) {
                            savePostToDB(category, title, content, fileData);
                        }
                    }))
                    .addOnFailureListener(e -> {
                        Toast.makeText(MyNewTextActivity.this, "파일 업로드 실패: " + fileName, Toast.LENGTH_SHORT).show();
                    });
        }
    }

    // URI에서 파일 확장자 추출
    private String getFileExtension(Uri uri) {
        String extension = "";
        String mimeType = getContentResolver().getType(uri);
        if (mimeType != null) {
            extension = mimeType.substring(mimeType.lastIndexOf("/") + 1);
        } else {
            String path = uri.getPath();
            int dotIndex = path != null ? path.lastIndexOf('.') : -1;
            if (dotIndex != -1) {
                extension = path.substring(dotIndex + 1);
            }
        }
        return extension;
    }

    // 파일 업로드 시, 파일 선택과 관련된 로직
    private void selectFiles() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        filePickerLauncher.launch(intent);
    }

    // 파일 선택기를 실행하는 작업(Intent)와 결과를 처리하는 작업(result)을 연결하는 로직
    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    handleFileSelection(result.getData());
                }
            }
    );

    // 선택한 파일은 업데이트 파일(새 파일)로 업데이트 파일 리스트에 분리하여 관리
    private void handleFileSelection(Intent data) {
        if (data.getClipData() != null) {
           int count = data.getClipData().getItemCount();
           for (int i = 0; i < count; i++) {
               Uri fileUri = data.getClipData().getItemAt(i).getUri();
               String fileName = getFileName(fileUri);
               fileUris.add(fileUri);
               fileNames.add(fileName != null ? fileName : "파일 이름 없음");
           }
        } else if (data.getData() != null) {
            Uri fileUri = data.getData();
            String fileName = getFileName(fileUri);
            fileUris.add(fileUri);
            fileNames.add(fileName != null ? fileName : "파일 이름 없음");
        }

        updateUploadedFilesUI();
    }

    // 새로 업데이트된 파일의 이름을 가져오는 메서드
    private String getFileName(Uri uri) {
        String fileName = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            }
        }

        if (fileName == null) {
            String path = uri.getPath();
            int lastSlashIndex = path != null ? path.lastIndexOf('/') : -1;
            if (lastSlashIndex != -1) {
                fileName = path.substring(lastSlashIndex + 1);
            }
        }

        return fileName;
    }

    // 업로드된 파일 목록을 UI에 갱신
    private void updateUploadedFilesUI() {
        uploadedFileContainer.removeAllViews();

        for (int i = 0; i < fileNames.size(); i++) {
            String fileName = fileNames.get(i);
            Uri fileUri = fileUris.get(i);
            int finalI = i;

            View fileItemView = LayoutInflater.from(this).inflate(R.layout.my_new_text_uploaded_file, uploadedFileContainer, false);

            TextView fileTextView = fileItemView.findViewById(R.id.uploadedFileTextView);
            fileTextView.setText(fileName);
            fileTextView.setOnClickListener(v -> openFile(fileUri));

            ImageView deleteFileButton = fileItemView.findViewById(R.id.deleteFileButton);
            deleteFileButton.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("파일 삭제 확인")
                        .setMessage("정말 이 파일을 삭제하시겠습니까?")
                        .setPositiveButton("삭제", (dialog, which) -> {
                            fileUris.remove(finalI);
                            fileNames.remove(finalI);
                            updateUploadedFilesUI();
                            Toast.makeText(this, "파일이 삭제되었습니다: " + fileName, Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("취소", (dialog, which) -> {})
                        .show();
            });

            uploadedFileContainer.addView(fileItemView);
        }

        uploadedFileContainer.setVisibility(fileUris.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // 파일 이름을 클릭하면 파일을 열 수 있도록 Intent 실행
    private void openFile(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    // 현재 입력된 텍스트에 선택된 스타일을 적용
    private void applyCurrentStyleToInput() {
        int start = contentEditText.getSelectionStart();
        int end = contentEditText.getSelectionEnd();

        if (start < end) {
            Editable editable = contentEditText.getText();
            SpannableString spannableString = new SpannableString(editable);

            if (isBold) {
                boolean isAlreadyBold = false;
                for (StyleSpan span : spannableString.getSpans(start, end, StyleSpan.class)) {
                    if (span.getStyle() == Typeface.BOLD) {
                        isAlreadyBold = true;
                        break;
                    }
                }
                if (!isAlreadyBold) {
                    spannableString.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                for (StyleSpan span : spannableString.getSpans(start, end, StyleSpan.class)) {
                    if (span.getStyle() == Typeface.BOLD) {
                        spannableString.removeSpan(span);
                    }
                }
            }

            if (isItalic) {
                boolean isAlreadyItalic = false;
                for (StyleSpan span : spannableString.getSpans(start, end, StyleSpan.class)) {
                    if (span.getStyle() == Typeface.ITALIC) {
                        isAlreadyItalic = true;
                        break;
                    }
                }
                if (!isAlreadyItalic) {
                    spannableString.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                for (StyleSpan span : spannableString.getSpans(start, end, StyleSpan.class)) {
                    if (span.getStyle() == Typeface.ITALIC) {
                        spannableString.removeSpan(span);
                    }
                }
            }

            contentEditText.setText(spannableString);
            contentEditText.setSelection(start, end);
        }
    }

    // 서식 버튼 클릭 리스너 설정
    private void setupButtonListeners() {
        boldButton.setOnClickListener(v -> {
            isBold = !isBold;
            toggleStyle(contentEditText, new StyleSpan(android.graphics.Typeface.BOLD), isBold);
        });

        italicButton.setOnClickListener(v -> {
            isItalic = !isItalic;
            toggleStyle(contentEditText, new StyleSpan(android.graphics.Typeface.ITALIC), isItalic);
        });

        underlineButton.setOnClickListener(v -> {
            isUnderline = !isUnderline;
            toggleStyle(contentEditText, new UnderlineSpan(), isUnderline);
        });

        strikethroughButton.setOnClickListener(v -> {
            isStrikethrough = !isStrikethrough;
            toggleStyle(contentEditText, new StrikethroughSpan(), isStrikethrough);
        });

        imageButton.setOnClickListener(v -> {
            Toast.makeText(this, "이미지 추가 버튼 클릭됨", Toast.LENGTH_SHORT).show();
        });
    }

    // 텍스트 범위에 스타일 적용 및 제거
    private void toggleStyle(EditText editText, Object style, boolean isEnabled) {
        Editable text = editText.getText();
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();

        if (isEnabled) {
            text.setSpan(style, start, end, Editable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            Object[] spans = text.getSpans(start, end, style.getClass());
            for (Object span : spans) {
                text.removeSpan(span);
            }
        }
    }

    // 텍스트 변경 이벤트를 감지하여 스타일을 적용
    private void setupTextWatcher() {
        contentEditText.addTextChangedListener(new TextWatcher() {
            private int start;
            private int count;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                this.start = start;
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                this.count = count;
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (count > 0) {
                    applyCurrentStyles(s, start, start + count);
                }
            }
        });
    }

    // 텍스트 변경 시 현재 스타일 적용
    private void applyCurrentStyles(Editable s, int start, int end) {
        if (isBold) s.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (isItalic) s.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (isUnderline) s.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (isStrikethrough) s.setSpan(new StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    // 텍스트를 HTML 스타일로 변환
    private String convertToHtmlStyledContent(String content) {
        StringBuilder htmlContent = new StringBuilder();
        Editable text = contentEditText.getText();

        boolean isBold = false, isItalic = false, isUnderline = false, isStrikethrough = false;

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);

            boolean bold = false, italic = false, underline = false, strikethrough = false;

            // 현재 위치의 스타일 검사
            for (StyleSpan span : text.getSpans(i, i + 1, StyleSpan.class)) {
                if (span.getStyle() == Typeface.BOLD) bold = true;
                if (span.getStyle() == Typeface.ITALIC) italic = true;
            }
            if (text.getSpans(i, i + 1, UnderlineSpan.class).length > 0) underline = true;
            if (text.getSpans(i, i + 1, StrikethroughSpan.class).length > 0) strikethrough = true;

            // 스타일이 변경되면 이전 스타일 태그 닫기
            if (isBold && !bold) htmlContent.append("</b>");
            if (isItalic && !italic) htmlContent.append("</i>");
            if (isUnderline && !underline) htmlContent.append("</u>");
            if (isStrikethrough && !strikethrough) htmlContent.append("</s>");

            // 새로운 스타일 태그 열기
            if (!isBold && bold) htmlContent.append("<b>");
            if (!isItalic && italic) htmlContent.append("<i>");
            if (!isUnderline && underline) htmlContent.append("<u>");
            if (!isStrikethrough && strikethrough) htmlContent.append("<s>");

            // 현재 문자 추가
            htmlContent.append(ch);

            // 현재 스타일 상태 업데이트
            isBold = bold;
            isItalic = italic;
            isUnderline = underline;
            isStrikethrough = strikethrough;
        }

        // 남아있는 스타일 태그 닫기
        if (isStrikethrough) htmlContent.append("</s>");
        if (isUnderline) htmlContent.append("</u>");
        if (isItalic) htmlContent.append("</i>");
        if (isBold) htmlContent.append("</b>");

        return htmlContent.toString();
    }

}
