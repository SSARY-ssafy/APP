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
    private Button boldButton, italicButton, underlineButton, strikethroughButton;
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
    private boolean isBold = false, isItalic = false, isUnderline = false, isStrikethrough = false;

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
        underlineButton = findViewById(R.id.underlineButton);
        strikethroughButton = findViewById(R.id.strikethroughButton);
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

        setupButtonListeners();
        setupTextWatcher();

//        submitPostButton.setOnClickListener(v -> savePostToFirestore());

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


//    private void savePostToFirestore(String category, String title, String content, String fileName, String fileUrl) {
//        Map<String, Object> post = new HashMap<>();
//        String htmlContent = convertToHtmlStyledContent(content);
//        post.put("category", category);
//        post.put("title", title);
//        post.put("content", htmlContent);
//        post.put("fileName", fileName);  // 파일 이름 저장
//        post.put("fileUrl", fileUrl);    // 파일 URL 저장
//
//        db.collection("posts").add(post)
//                .addOnSuccessListener(documentReference -> {
//                    Toast.makeText(MyNewTextActivity.this, "글이 성공적으로 저장되었습니다.", Toast.LENGTH_SHORT).show();
//                    Intent resultIntent = new Intent();
//                    resultIntent.putExtra("updatedCategory", category);
//                    setResult(RESULT_OK, resultIntent);
//                    finish();
//                })
//                .addOnFailureListener(e -> Toast.makeText(MyNewTextActivity.this, "글 저장에 실패했습니다.", Toast.LENGTH_SHORT).show());
//    }

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

//    private void updateButtonStyle(Button button, boolean isActive) {
//        if (isActive) {
//            button.setBackgroundColor(getResources().getColor(R.color.colorAccent));
//            button.setTextColor(getResources().getColor(android.R.color.white));
//        } else {
//            button.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
//            button.setTextColor(getResources().getColor(android.R.color.black));
//        }
//    }

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



    private void setupButtonListeners() {
        boldButton.setOnClickListener(v -> {
            isBold = !isBold;
            applyCurrentStyleToSelection(Typeface.BOLD, isBold);
            updateButtonStyle(boldButton, isBold);
        });

        italicButton.setOnClickListener(v -> {
            isItalic = !isItalic;
            applyCurrentStyleToSelection(Typeface.ITALIC, isItalic);
            updateButtonStyle(italicButton, isItalic);
        });

        underlineButton.setOnClickListener(v -> {
            isUnderline = !isUnderline;
            applyCurrentSpanToSelection(new UnderlineSpan(), isUnderline);
            updateButtonStyle(underlineButton, isUnderline);
        });

        strikethroughButton.setOnClickListener(v -> {
            isStrikethrough = !isStrikethrough;
            applyCurrentSpanToSelection(new StrikethroughSpan(), isStrikethrough);
            updateButtonStyle(strikethroughButton, isStrikethrough);
        });
    }

    private void setupTextWatcher() {
        postContentEditText.addTextChangedListener(new TextWatcher() {
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

    private void applyCurrentStyleToSelection(int style, boolean enable) {
        Editable text = postContentEditText.getText();
        int start = postContentEditText.getSelectionStart();
        int end = postContentEditText.getSelectionEnd();

        if (enable) {
            text.setSpan(new StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            StyleSpan[] spans = text.getSpans(start, end, StyleSpan.class);
            for (StyleSpan span : spans) {
                if (span.getStyle() == style) {
                    text.removeSpan(span);
                }
            }
        }
    }

    private void applyCurrentSpanToSelection(Object span, boolean enable) {
        Editable text = postContentEditText.getText();
        int start = postContentEditText.getSelectionStart();
        int end = postContentEditText.getSelectionEnd();

        if (enable) {
            text.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            if (span instanceof UnderlineSpan) {
                UnderlineSpan[] spans = text.getSpans(start, end, UnderlineSpan.class);
                for (UnderlineSpan uSpan : spans) {
                    text.removeSpan(uSpan);
                }
            } else if (span instanceof StrikethroughSpan) {
                StrikethroughSpan[] spans = text.getSpans(start, end, StrikethroughSpan.class);
                for (StrikethroughSpan sSpan : spans) {
                    text.removeSpan(sSpan);
                }
            }
        }
    }

    private void applyCurrentStyles(Editable s, int start, int end) {
        if (isBold) s.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (isItalic) s.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (isUnderline) s.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (isStrikethrough) s.setSpan(new StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
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

//    private void savePostToFirestore() {
////        String category = categorySpinner.getSelectedItem().toString();
//        String category = getIntent().getStringExtra("category");
//        String title = postTitleEditText.getText().toString().trim();
//        String content = postContentEditText.getText().toString();
//
//        if (title.isEmpty() || content.isEmpty()) {
//            Toast.makeText(this, "제목과 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        Map<String, Object> post = new HashMap<>();
//        post.put("category", category);
//        post.put("title", title);
//        post.put("content", content);
//
//        String htmlContent = convertToHtmlStyledContent(content);
//        post.put("styledContent", htmlContent);
//
//        db.collection("posts").add(post)
//                .addOnSuccessListener(documentReference -> {
//                    Toast.makeText(MyNewTextActivity.this, "글이 성공적으로 저장되었습니다.", Toast.LENGTH_SHORT).show();
//                    finish();
//                })
//                .addOnFailureListener(e -> Toast.makeText(MyNewTextActivity.this, "글 저장에 실패했습니다.", Toast.LENGTH_SHORT).show());
//    }

    private String convertToHtmlStyledContent(String content) {
        StringBuilder htmlContent = new StringBuilder();
        Editable text = postContentEditText.getText();

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


//    private String convertToHtmlStyledContent(String content) {
//        StringBuilder htmlContent = new StringBuilder();
//        Editable text = postContentEditText.getText();
//
//        for (int i = 0; i < content.length(); i++) {
//            char ch = content.charAt(i);
//            boolean isBold = false, isItalic = false, isUnderline = false, isStrikethrough = false;
//
//            for (StyleSpan span : text.getSpans(i, i + 1, StyleSpan.class)) {
//                if (span.getStyle() == Typeface.BOLD) isBold = true;
//                if (span.getStyle() == Typeface.ITALIC) isItalic = true;
//            }
//            if (text.getSpans(i, i + 1, UnderlineSpan.class).length > 0) isUnderline = true;
//            if (text.getSpans(i, i + 1, StrikethroughSpan.class).length > 0) isStrikethrough = true;
//
//            if (isBold) htmlContent.append("<b>");
//            if (isItalic) htmlContent.append("<i>");
//            if (isUnderline) htmlContent.append("<u>");
//            if (isStrikethrough) htmlContent.append("<s>");
//
//            htmlContent.append(ch);
//
//            if (isStrikethrough) htmlContent.append("</s>");
//            if (isUnderline) htmlContent.append("</u>");
//            if (isItalic) htmlContent.append("</i>");
//            if (isBold) htmlContent.append("</b>");
//        }
//
//        return htmlContent.toString();
//    }
}
