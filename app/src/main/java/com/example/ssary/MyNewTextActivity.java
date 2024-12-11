package com.example.ssary;

import android.content.Intent;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

    private static final int PICK_FILE_REQUEST = 1;

    private TextView categoryTextView, uploadedFileTextView;
    private EditText titleEditText, contentEditText;
    private LinearLayout uploadedFileContainer;
    private ImageView boldButton, italicButton, underlineButton, strikethroughButton, uploadFileButton, imageButton, changeFileButton, deleteFileButton;
    private Button submitPostButton;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private Uri fileUri;
    private String fileName;

    private boolean isBold = false, isItalic = false, isUnderline = false, isStrikethrough = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_new_text);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance(); // Firebase Storage 초기화

        categoryTextView = findViewById(R.id.categoryTextView);
        titleEditText = findViewById(R.id.titleEditText);
        contentEditText = findViewById(R.id.contentEditText);
        uploadedFileContainer = findViewById(R.id.uploadedFileContainer);
        boldButton = findViewById(R.id.boldButton);
        italicButton = findViewById(R.id.italicButton);
        underlineButton = findViewById(R.id.underlineButton);
        strikethroughButton = findViewById(R.id.strikethroughButton);
        uploadFileButton = findViewById(R.id.uploadFileButton);
        imageButton = findViewById(R.id.imageButton);
        submitPostButton = findViewById(R.id.submitPostButton);
        changeFileButton = findViewById(R.id.changeFileButton);
        deleteFileButton = findViewById(R.id.deleteFileButton);
        uploadedFileTextView = findViewById(R.id.uploadedFileTextView);
        uploadedFileTextView.setPaintFlags(uploadedFileTextView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

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
        String documentId = getIntent().getStringExtra("documentId");

        // 카테고리 이름을 categoryTextView에 설정
        if (category != null && !category.isEmpty()) {
            categoryTextView.setText("카테고리: " + category);
        } else {
            categoryTextView.setText("카테고리 없음");
        }

        if (title != null && content != null) {
            titleEditText.setText(title);
            contentEditText.setText(content);
            boolean isEditMode = true;
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
            String postTitle = titleEditText.getText().toString().trim();
            String postContent = contentEditText.getText().toString().trim();

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

    private void savePostToFirestore(String category, String title, String content, String fileName, String fileUrl) {
        Map<String, Object> post = new HashMap<>();
        String htmlContent = convertToHtmlStyledContent(content);

        post.put("category", category);
        post.put("title", title);
        post.put("content", htmlContent);
        post.put("fileName", fileName);
        post.put("fileUrl", fileUrl);

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

    private void setupTextWatcher() {
        contentEditText.addTextChangedListener(new TextWatcher() {
            private int start, before, count;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                this.start = start;
                this.before = count;
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

    private void applyCurrentStyles(Editable s, int start, int end) {
        if (isBold) s.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (isItalic) s.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (isUnderline) s.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (isStrikethrough) s.setSpan(new StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

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
