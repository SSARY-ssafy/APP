package com.example.ssary;

import android.content.Intent;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextWatcher;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MyNewTextActivity extends AppCompatActivity {

    private static final int PICK_FILE_REQUEST = 1; // 파일 선택 코드

    private TextView categoryTextView;
    private EditText postTitleEditText;
    private EditText postContentEditText;
    private Button submitPostButton;
    private Button uploadFileButton;
    private Button boldButton;
    private Button italicButton;
    private Button changeFileButton;
    private Button deleteFileButton;
    private TextView uploadedFileTextView; // 업로드된 파일 정보를 표시할 TextView
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private Uri fileUri; // 선택한 파일의 Uri
    private String fileName; // 파일 이름
    private LinearLayout uploadedFileContainer; // 파일 이름과 아이콘 컨테이너

    private boolean isEditMode = false;
    private String documentId;
    private boolean isBold = false;
    private boolean isItalic = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_new_text);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance(); // Firebase Storage 초기화

        categoryTextView = findViewById(R.id.categoryTextView);
        postTitleEditText = findViewById(R.id.postTitleEditText);
        postContentEditText = findViewById(R.id.postContentEditText);
        submitPostButton = findViewById(R.id.submitPostButton);
        boldButton = findViewById(R.id.boldButton);
        italicButton = findViewById(R.id.italicButton);
        uploadFileButton = findViewById(R.id.uploadFileButton);
        changeFileButton = findViewById(R.id.changeFileButton);
        deleteFileButton = findViewById(R.id.deleteFileButton);
        uploadedFileTextView = findViewById(R.id.uploadedFileTextView);
        uploadedFileTextView.setPaintFlags(uploadedFileTextView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        uploadedFileContainer = findViewById(R.id.uploadedFileContainer);

        uploadFileButton.setOnClickListener(v -> selectFile());
        changeFileButton.setOnClickListener(v -> selectFile());  // 바로 새 파일 선택
        deleteFileButton.setOnClickListener(v -> {
            fileUri = null;
            uploadedFileTextView.setText("업로드된 파일이 없습니다.");
            uploadedFileContainer.setVisibility(View.GONE);
        });

        // 인텐트에서 카테고리 및 글 정보를 받아와 설정
        String category = getIntent().getStringExtra("category");
        String title = getIntent().getStringExtra("title");
        String content = getIntent().getStringExtra("content");
        documentId = getIntent().getStringExtra("documentId");

        // 카테고리 이름을 categoryTextView에 설정
        if (category != null && !category.isEmpty()) {
            categoryTextView.setText("카테고리: " + category);
        } else {
            categoryTextView.setText("카테고리 없음");
        }

        if (title != null && content != null) {
            postTitleEditText.setText(title);
            postContentEditText.setText(content);
            isEditMode = true;
            submitPostButton.setText("글 수정");
        }

        // 파일 이름 클릭 시 열기
        uploadedFileTextView.setOnClickListener(v -> {
            if (fileUri != null) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(fileUri, "*/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } else {
                Toast.makeText(this, "업로드된 파일이 없습니다.", Toast.LENGTH_SHORT).show();
            }
        });

        submitPostButton.setOnClickListener(v -> {
            String postTitle = postTitleEditText.getText().toString().trim();
            String postContent = postContentEditText.getText().toString().trim();

            if (postTitle.isEmpty() || postContent.isEmpty()) {
                Toast.makeText(MyNewTextActivity.this, "제목과 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            } else {
                if (fileUri != null) {
                    // 파일이 선택된 경우 Firebase Storage에 업로드한 후 Firestore에 파일 이름과 URL 함께 저장
                    uploadFileToFirebase(postTitle, postContent, category);
                } else {
                    // 파일이 선택되지 않은 경우, 파일 이름과 URL을 null로 저장
                    savePostToFirestore(category, postTitle, postContent, null, null);
                }
            }
        });


        boldButton.setOnClickListener(v -> {
            isBold = !isBold;
            updateButtonStyle(boldButton, isBold);
            applyCurrentStyleToInput();
        });

        italicButton.setOnClickListener(v -> {
            isItalic = !isItalic;
            updateButtonStyle(italicButton, isItalic);
            applyCurrentStyleToInput();
        });

        postContentEditText.addTextChangedListener(new TextWatcher() {
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

    private void savePostToFirestore(String category, String title, String content, String fileName, String fileUrl) {
        Map<String, Object> post = new HashMap<>();
        post.put("category", category);
        post.put("title", title);
        post.put("content", content);
        post.put("fileName", fileName);  // 파일 이름 저장
        post.put("fileUrl", fileUrl);    // 파일 URL 저장

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

    private void uploadFileToFirebase(String title, String content, String category) {
        fileName = fileUri.getLastPathSegment();  // 원본 파일 이름
        String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName; // 고유 식별자 추가
        StorageReference fileRef = storage.getReference().child("uploads/" + uniqueFileName);

        fileRef.putFile(fileUri)
                .addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String uploadedFileUrl = uri.toString();
                    savePostToFirestore(category, title, content, fileName, uploadedFileUrl); // Firestore에 파일 이름과 URL 저장
                }))
                .addOnFailureListener(e -> {
                    Toast.makeText(MyNewTextActivity.this, "파일 업로드 실패", Toast.LENGTH_SHORT).show();
                });
    }

    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_FILE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            fileUri = data.getData();
            fileName = fileUri.getLastPathSegment();
            uploadedFileTextView.setText(fileName != null ? fileName : "파일 이름을 불러올 수 없음");
            uploadedFileContainer.setVisibility(View.VISIBLE);
        }
    }

    private void updateButtonStyle(Button button, boolean isActive) {
        if (isActive) {
            button.setBackgroundColor(getResources().getColor(R.color.colorAccent));
            button.setTextColor(getResources().getColor(android.R.color.white));
        } else {
            button.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
            button.setTextColor(getResources().getColor(android.R.color.black));
        }
    }

    private void applyCurrentStyleToInput() {
        int start = postContentEditText.getSelectionStart();
        int end = postContentEditText.getSelectionEnd();

        if (start < end) {
            Editable editable = postContentEditText.getText();
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

            postContentEditText.setText(spannableString);
            postContentEditText.setSelection(start, end);
        }
    }
}
