package com.example.ssary;

import android.text.Editable;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import android.text.TextWatcher;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MyTextfileActivity extends AppCompatActivity {

    private Spinner categorySpinner;
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
        setContentView(R.layout.activity_my_textfile);

        db = FirebaseFirestore.getInstance();

        categorySpinner = findViewById(R.id.categorySpinner);
        postTitleEditText = findViewById(R.id.postTitleEditText);
        postContentEditText = findViewById(R.id.postContentEditText);
        submitPostButton = findViewById(R.id.submitPostButton);
        boldButton = findViewById(R.id.boldButton);
        italicButton = findViewById(R.id.italicButton);

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

        String title = getIntent().getStringExtra("title");
        String content = getIntent().getStringExtra("content");
        String category = getIntent().getStringExtra("category");
        documentId = getIntent().getStringExtra("documentId");

        if (title != null && content != null) {
            postTitleEditText.setText(title);
            postContentEditText.setText(content);
            if (category != null) {
                int categoryIndex = categoryList.indexOf(category);
                if (categoryIndex != -1) {
                    categorySpinner.setSelection(categoryIndex);
                }
            }
            isEditMode = true;
            submitPostButton.setText("글 수정");
        } else {
            int defaultCategoryIndex = categoryList.indexOf("자소서");
            if (defaultCategoryIndex != -1) {
                categorySpinner.setSelection(defaultCategoryIndex);
            }
        }

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

        boldButton.setOnClickListener(v -> {
            isBold = !isBold;
            updateButtonStyle(boldButton, isBold);
            applyCurrentStyleToInput(); // Apply style immediately on button click
        });

        italicButton.setOnClickListener(v -> {
            isItalic = !isItalic;
            updateButtonStyle(italicButton, isItalic);
            applyCurrentStyleToInput(); // Apply style immediately on button click
        });

        postContentEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyCurrentStyleToInput(); // Apply current styles as text changes
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
    // Firestore에 글 저장 메서드(오리지널)
//    private void savePostToFirestore(String category, String title, String content) {
//        Map<String, Object> post = new HashMap<>();
//        post.put("category", category);
//        post.put("title", title);
//        post.put("content", content);
//
//        db.collection("posts").add(post)
//                .addOnSuccessListener(documentReference -> {
//                    Toast.makeText(MyTextfileActivity.this, "글이 성공적으로 저장되었습니다.", Toast.LENGTH_SHORT).show();
//                    Intent resultIntent = new Intent();
//                    resultIntent.putExtra("updatedCategory", category);
//                    setResult(RESULT_OK, resultIntent);
//                    finish();
//                })
//                .addOnFailureListener(e -> Toast.makeText(MyTextfileActivity.this, "글 저장에 실패했습니다.", Toast.LENGTH_SHORT).show());
//    }

    // Firestore에 글 저장 메서드(한글자씩 스타일 저장)
//    private void savePostToFirestore(String category, String title, String content) {
//        Map<String, Object> post = new HashMap<>();
//        post.put("category", category);
//        post.put("title", title);
//        post.put("content", content);
//
//        // 스타일 정보 추가
//        ArrayList<Map<String, Object>> styledContent = new ArrayList<>();
//        int start = 0;
//
//        while (start < content.length()) {
//            int end = start + 1; // 한 글자씩 처리
//            boolean isBold = false; // 볼드 스타일 여부
//            boolean isItalic = false; // 이탤릭 스타일 여부
//
//            // 스타일을 검사하여 적용
//            if (postContentEditText.getText().getSpans(start, end, StyleSpan.class).length > 0) {
//                for (StyleSpan span : postContentEditText.getText().getSpans(start, end, StyleSpan.class)) {
//                    if (span.getStyle() == Typeface.BOLD) {
//                        isBold = true;
//                    }
//                    if (span.getStyle() == Typeface.ITALIC) {
//                        isItalic = true;
//                    }
//                }
//            }
//
//            Map<String, Object> styledPart = new HashMap<>();
//            styledPart.put("text", String.valueOf(content.charAt(start)));
//            styledPart.put("bold", isBold);
//            styledPart.put("italic", isItalic);
//            styledContent.add(styledPart);
//
//            start++;
//        }
//
//        post.put("styledContent", styledContent);
//
//        db.collection("posts").add(post)
//                .addOnSuccessListener(documentReference -> {
//                    Toast.makeText(MyTextfileActivity.this, "글이 성공적으로 저장되었습니다.", Toast.LENGTH_SHORT).show();
//                    Intent resultIntent = new Intent();
//                    resultIntent.putExtra("updatedCategory", category);
//                    setResult(RESULT_OK, resultIntent);
//                    finish(); // 현재 Activity 종료
//                })
//                .addOnFailureListener(e -> Toast.makeText(MyTextfileActivity.this, "글 저장에 실패했습니다.", Toast.LENGTH_SHORT).show());
//    }

    // Firestore에 글 저장 메서드(html식으로 스타일 저장)
    private void savePostToFirestore(String category, String title, String content) {
        Map<String, Object> post = new HashMap<>();
        post.put("category", category);
        post.put("title", title);
        post.put("content", content);

        // HTML 형식으로 스타일 적용
        String htmlStyledContent = convertToHtmlStyledContent(content);
        post.put("styledContent", htmlStyledContent);

        db.collection("posts").add(post)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(MyTextfileActivity.this, "글이 성공적으로 저장되었습니다.", Toast.LENGTH_SHORT).show();
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("updatedCategory", category);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(MyTextfileActivity.this, "글 저장에 실패했습니다.", Toast.LENGTH_SHORT).show());
    }

    private String convertToHtmlStyledContent(String content) {
        StringBuilder htmlContent = new StringBuilder();
        int start = 0;

        while (start < content.length()) {
            int end = start + 1; // 한 글자씩 처리
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


    // 한글자씩
//    private void updatePostInFirestore(String documentId, String category, String title, String content) {
//        Map<String, Object> updatedPost = new HashMap<>();
//        updatedPost.put("category", category);
//        updatedPost.put("title", title);
//        updatedPost.put("content", content);
//
//        db.collection("posts").document(documentId)
//                .update(updatedPost)
//                .addOnSuccessListener(aVoid -> {
//                    Toast.makeText(MyTextfileActivity.this, "글이 성공적으로 수정되었습니다.", Toast.LENGTH_SHORT).show();
//                    Intent resultIntent = new Intent();
//                    resultIntent.putExtra("updatedCategory", category);
//                    setResult(RESULT_OK, resultIntent);
//                    finish();
//                })
//                .addOnFailureListener(e -> Toast.makeText(MyTextfileActivity.this, "글 수정에 실패했습니다.", Toast.LENGTH_SHORT).show());
//    }

    // html방식으로 바꾸며 변경한 메서드
    private void updatePostInFirestore(String documentId, String category, String title, String content) {
        Map<String, Object> updatedPost = new HashMap<>();
        updatedPost.put("category", category);
        updatedPost.put("title", title);
        updatedPost.put("content", content);

        // HTML 형식으로 스타일 적용
        String htmlStyledContent = convertToHtmlStyledContent(content);
        updatedPost.put("styledContent", htmlStyledContent);

        db.collection("posts").document(documentId)
                .update(updatedPost)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(MyTextfileActivity.this, "글이 성공적으로 수정되었습니다.", Toast.LENGTH_SHORT).show();
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("updatedCategory", category);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(MyTextfileActivity.this, "글 수정에 실패했습니다.", Toast.LENGTH_SHORT).show());
    }

    // 한글자씩
//    private void applyCurrentStyleToInput() {
//        int start = postContentEditText.getSelectionStart();
//        int end = postContentEditText.getSelectionEnd();
//
//        if (start < end) {
//            Editable editable = postContentEditText.getText();
//            SpannableString spannableString = new SpannableString(editable);
//
//            // Apply current styles to the selected text
//            if (isBold) {
//                spannableString.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            }
//
//            if (isItalic) {
//                spannableString.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            }
//
//            postContentEditText.setText(spannableString);
//            postContentEditText.setSelection(start, end);
//        }
//    }

    // html방식
    private void applyCurrentStyleToInput() {
        int start = postContentEditText.getSelectionStart();
        int end = postContentEditText.getSelectionEnd();

        if (start < end) {
            Editable editable = postContentEditText.getText();
            SpannableString spannableString = new SpannableString(editable);

            // Apply current styles to the selected text
            if (isBold) {
                spannableString.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (isItalic) {
                spannableString.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
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
