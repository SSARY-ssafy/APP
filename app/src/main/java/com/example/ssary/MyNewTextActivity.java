package com.example.ssary;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextWatcher;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MyNewTextActivity extends AppCompatActivity {

    private TextView categoryTextView;
    private EditText postTitleEditText;
    private EditText postContentEditText;
    private Button submitPostButton;
    private Button boldButton;
    private Button italicButton;
    private FirebaseFirestore db;

    private boolean isEditMode = false;
    private String documentId;
    private boolean isBold = false;
    private boolean isItalic = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_new_text);

        db = FirebaseFirestore.getInstance();

        categoryTextView = findViewById(R.id.categoryTextView);
        postTitleEditText = findViewById(R.id.postTitleEditText);
        postContentEditText = findViewById(R.id.postContentEditText);
        submitPostButton = findViewById(R.id.submitPostButton);
        boldButton = findViewById(R.id.boldButton);
        italicButton = findViewById(R.id.italicButton);

        // 인텐트에서 카테고리 및 글 정보를 받아와 설정
        String category = getIntent().getStringExtra("category");
        String title = getIntent().getStringExtra("title");
        String content = getIntent().getStringExtra("content");
        documentId = getIntent().getStringExtra("documentId");

        // 카테고리 이름을 categoryTextView에 설정
        if (category != null && !category.isEmpty()) {
            categoryTextView.setText("카테고리: " + category);  // "카테고리: [카테고리 이름]" 형식으로 표시
        } else {
            categoryTextView.setText("카테고리 없음");  // 기본값
        }


        if (title != null && content != null) {
            postTitleEditText.setText(title);
            postContentEditText.setText(content);
            isEditMode = true;
            submitPostButton.setText("글 수정");
        }

        submitPostButton.setOnClickListener(v -> {
            String postTitle = postTitleEditText.getText().toString().trim();
            String postContent = postContentEditText.getText().toString().trim();

            if (postTitle.isEmpty() || postContent.isEmpty()) {
                Toast.makeText(MyNewTextActivity.this, "제목과 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            } else {
                if (isEditMode) {
                    updatePostInFirestore(documentId, category, postTitle, postContent);
                } else {
                    savePostToFirestore(category, postTitle, postContent);
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

    private void savePostToFirestore(String category, String title, String content) {
        Map<String, Object> post = new HashMap<>();
        post.put("category", category);
        post.put("title", title);
        post.put("content", content);

        String htmlStyledContent = convertToHtmlStyledContent(content);
        post.put("styledContent", htmlStyledContent);

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

    private void updatePostInFirestore(String documentId, String category, String title, String content) {
        Map<String, Object> updatedPost = new HashMap<>();
        updatedPost.put("category", category);
        updatedPost.put("title", title);
        updatedPost.put("content", content);

        String htmlStyledContent = convertToHtmlStyledContent(content);
        updatedPost.put("styledContent", htmlStyledContent);

        db.collection("posts").document(documentId)
                .update(updatedPost)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(MyNewTextActivity.this, "글이 성공적으로 수정되었습니다.", Toast.LENGTH_SHORT).show();
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("updatedCategory", category);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(MyNewTextActivity.this, "글 수정에 실패했습니다.", Toast.LENGTH_SHORT).show());
    }

    private String convertToHtmlStyledContent(String content) {
        StringBuilder htmlContent = new StringBuilder();
        int start = 0;

        while (start < content.length()) {
            int end = start + 1;
            boolean isBold = false;
            boolean isItalic = false;

            if (postContentEditText.getText().getSpans(start, end, StyleSpan.class).length > 0) {
                for (StyleSpan span : postContentEditText.getText().getSpans(start, end, StyleSpan.class)) {
                    if (span.getStyle() == Typeface.BOLD) {
                        isBold = true;
                    }
                    if (span.getStyle() == Typeface.ITALIC) {
                        isItalic = true;
                    }
                }
            }

            if (isBold) htmlContent.append("<b>");
            if (isItalic) htmlContent.append("<i>");

            htmlContent.append(content.charAt(start));

            if (isItalic) htmlContent.append("</i>");
            if (isBold) htmlContent.append("</b>");

            start++;
        }

        return htmlContent.toString();
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

    private void updateButtonStyle(Button button, boolean isActive) {
        if (isActive) {
            button.setBackgroundColor(getResources().getColor(R.color.colorAccent));
            button.setTextColor(getResources().getColor(android.R.color.white));
        } else {
            button.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
            button.setTextColor(getResources().getColor(android.R.color.black));
        }
    }
}
