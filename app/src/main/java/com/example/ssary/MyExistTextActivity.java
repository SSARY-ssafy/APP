package com.example.ssary;

import android.app.DownloadManager;
import android.content.Intent;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.os.Environment;


import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MyExistTextActivity extends AppCompatActivity {

    private static final int PICK_FILE_REQUEST = 1; // 파일 선택 코드

    private TextView categoryTextView;
    private EditText postTitleEditText;
    private EditText postContentEditText;
    private TextView uploadedFileTextView;
    private LinearLayout uploadedFileContainer;
    private Button updatePostButton;
    private Button deletePostButton;
    private Button uploadFileButton;
    private Button changeFileButton;
    private Button deleteFileButton;
    private Button downloadFileButton;
    private Button savePostButton;
    private Button boldButton, italicButton, underlineButton, strikethroughButton;


    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private String fileName; // 파일 이름
    private String documentId;
    private String fileUrl; // 파일 URL (스토리지에서 가져옴)
    private Uri fileUri;    // 파일 URI

    private boolean isFileDeleted = false; // 파일 삭제 상태 추적
    private boolean isBold = false, isItalic = false, isUnderline = false, isStrikethrough = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_exist_text);

        // Firestore 및 Storage 초기화
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // 레이아웃 요소 초기화
        categoryTextView = findViewById(R.id.categoryTextView);
        postTitleEditText = findViewById(R.id.postTitleEditText);
        postContentEditText = findViewById(R.id.postContentEditText);

        updatePostButton = findViewById(R.id.updatePostButton);
        deletePostButton = findViewById(R.id.deletePostButton);

        uploadFileButton = findViewById(R.id.uploadFileButton);
        changeFileButton = findViewById(R.id.changeFileButton);
        deleteFileButton = findViewById(R.id.deleteFileButton);
        downloadFileButton = findViewById(R.id.downloadFileButton);
        savePostButton = findViewById(R.id.savePostButton);
        boldButton = findViewById(R.id.boldButton);
        italicButton = findViewById(R.id.italicButton);
        underlineButton = findViewById(R.id.underlineButton);
        strikethroughButton = findViewById(R.id.strikethroughButton);

        uploadedFileTextView = findViewById(R.id.uploadedFileTextView);
        uploadedFileTextView.setPaintFlags(uploadedFileTextView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        uploadedFileContainer = findViewById(R.id.uploadedFileContainer);

        enableEditing(false);

        // Intent에서 데이터 받기
        Intent intent = getIntent();
        String category = intent.getStringExtra("category");
        String title = intent.getStringExtra("title");
        documentId = intent.getStringExtra("documentId");

        // 카테고리 설정
        categoryTextView.setText("카테고리: " + category);

        // Firestore에서 글 정보 로드
        loadPostFromFirestore(documentId);

        // ============== 리스너 =================

//        updatePostButton.setOnClickListener(v -> updatePost());
        savePostButton.setOnClickListener(v -> updatePost());
        updatePostButton.setOnClickListener(v -> enableEditing(true));

        deletePostButton.setOnClickListener(v -> deletePost());
        uploadedFileTextView.setOnClickListener(v -> openFile());

        uploadFileButton.setOnClickListener(v -> selectFile());
        changeFileButton.setOnClickListener(v -> selectFile());  // 바로 새 파일 선택
        deleteFileButton.setOnClickListener(v -> {
            isFileDeleted = true; // 파일 삭제 상태로 설정
            uploadedFileTextView.setText("업로드된 파일이 없습니다.");
            uploadedFileContainer.setVisibility(View.GONE);
        });
        downloadFileButton.setOnClickListener(v -> downloadFile());


        setupButtonListeners();
        setupTextWatcher();
    }

    private void enableEditing(boolean isEditable) {
        postTitleEditText.setEnabled(isEditable);
        postContentEditText.setEnabled(isEditable);

        if (isEditable) {
            uploadFileButton.setVisibility(View.VISIBLE);
            downloadFileButton.setVisibility(View.GONE);
            changeFileButton.setVisibility(View.VISIBLE);
            deleteFileButton.setVisibility(View.VISIBLE);
            savePostButton.setVisibility(View.VISIBLE);
            updatePostButton.setVisibility(View.GONE);
        } else {
            downloadFileButton.setVisibility(View.VISIBLE);
            changeFileButton.setVisibility(View.GONE);
            deleteFileButton.setVisibility(View.GONE);
            savePostButton.setVisibility(View.GONE);
            updatePostButton.setVisibility(View.VISIBLE);
        }
    }

    private void downloadFile() {
        if (fileUrl != null && !fileUrl.isEmpty()) {
            Uri fileUri = Uri.parse(fileUrl);
            String fileName = uploadedFileTextView.getText().toString();

            // DownloadManager를 사용하여 파일 다운로드
            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(fileUri);
            request.setTitle(fileName);
            request.setDescription("파일 다운로드 중...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            // 다운로드 요청 실행
            downloadManager.enqueue(request);

            Toast.makeText(this, "파일 다운로드를 시작합니다.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "다운로드할 파일이 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_FILE_REQUEST);
    }

    // Firestore에서 글 정보 로드
    private void loadPostFromFirestore(String documentId) {
        db.collection("posts").document(documentId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String title = documentSnapshot.getString("title");
//                        String content = documentSnapshot.getString("content");
                        String styledContent = documentSnapshot.getString("content");
                        postTitleEditText.setText(title);

                        // HTML 형식의 styledContent를 파싱하여 표시
                        if (styledContent != null && !styledContent.isEmpty()) {
                            CharSequence styledText = parseHtmlContent(styledContent);
                            postContentEditText.setText(styledText);
                        } else {
                            postContentEditText.setText("NONE");
                        }




                        fileUrl = documentSnapshot.getString("fileUrl");



                        // 파일 정보 표시
                        if (fileUrl != null && !fileUrl.isEmpty()) {
                            String fileName = documentSnapshot.getString("fileName");
                            uploadedFileTextView.setText(fileName != null ? fileName : "파일 이름 없음");
                            uploadedFileContainer.setVisibility(View.VISIBLE);
                        }
                    } else {
                        Toast.makeText(this, "글 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "글을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
    }

    private CharSequence parseHtmlContent(String htmlContent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            // Nougat 이상 버전에서는 FROM_HTML_MODE_LEGACY 사용
            return Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY);
        } else {
            // 이전 버전에서는 Html.fromHtml만 사용
            return Html.fromHtml(htmlContent);
        }
    }



//    // 굵은 글씨 적용
//    private void applyBoldSpan(Spannable spannable) {
//        String text = spannable.toString();
//        int start, end;
//
//        while ((start = text.indexOf("<b>")) != -1 && (end = text.indexOf("</b>")) != -1) {
//            spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end - 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            spannable.delete(end, end + 4); // 닫는 태그 삭제
//            spannable.delete(start, start + 3); // 여는 태그 삭제
//            text = spannable.toString();
//        }
//    }
//
//    // 기울임 글씨 적용
//    private void applyItalicSpan(Spannable spannable) {
//        String text = spannable.toString();
//        int start, end;
//
//        while ((start = text.indexOf("<i>")) != -1 && (end = text.indexOf("</i>")) != -1) {
//            spannable.setSpan(new StyleSpan(Typeface.ITALIC), start, end - 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            spannable.delete(end, end + 4);
//            spannable.delete(start, start + 3);
//            text = spannable.toString();
//        }
//    }
//
//    // 밑줄 적용
//    private void applyUnderlineSpan(Spannable spannable) {
//        String text = spannable.toString();
//        int start, end;
//
//        while ((start = text.indexOf("<u>")) != -1 && (end = text.indexOf("</u>")) != -1) {
//            spannable.setSpan(new UnderlineSpan(), start, end - 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            spannable.delete(end, end + 4);
//            spannable.delete(start, start + 3);
//            text = spannable.toString();
//        }
//    }
//
//    // 취소선 적용
//    private void applyStrikethroughSpan(Spannable spannable) {
//        String text = spannable.toString();
//        int start, end;
//
//        while ((start = text.indexOf("<s>")) != -1 && (end = text.indexOf("</s>")) != -1) {
//            spannable.setSpan(new StrikethroughSpan(), start, end - 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            spannable.delete(end, end + 4);
//            spannable.delete(start, start + 3);
//            text = spannable.toString();
//        }
//    }










//    private void loadPostFromFirestore(String documentId) {
//        db.collection("posts").document(documentId)
//                .get()
//                .addOnSuccessListener(documentSnapshot -> {
//                    if (documentSnapshot.exists()) {
//                        // Firestore에서 글 제목, 내용, 파일 정보 로드
//                        String title = documentSnapshot.getString("title");
//                        String content = documentSnapshot.getString("content");
//                        fileUrl = documentSnapshot.getString("fileUrl");
//
//                        postTitleEditText.setText(title);
//                        postContentEditText.setText(content);
//
//                        // 파일이 있는 경우 파일 이름을 표시하고 컨테이너 보이기
//                        if (fileUrl != null && !fileUrl.isEmpty()) {
//                            String fileName = documentSnapshot.getString("fileName");
//                            uploadedFileTextView.setText(fileName != null ? fileName : "파일 이름 없음");
//                            uploadedFileContainer.setVisibility(View.VISIBLE);
//                        }
//                    } else {
//                        Toast.makeText(this, "글 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
//                    }
//                })
//                .addOnFailureListener(e -> {
//                    Toast.makeText(this, "글을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
//                });
//    }

    private void openFile() {
        if (fileUrl != null) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(fileUrl), "*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } else {
            Toast.makeText(this, "업로드된 파일이 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePost() {
        String updatedTitle = postTitleEditText.getText().toString().trim();
        String updatedContents = postContentEditText.getText().toString().trim();

        String updatedContent = convertToHtmlStyledContent(updatedContents);

        if (updatedTitle.isEmpty() || updatedContent.isEmpty()) {
            Toast.makeText(this, "제목과 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isFileDeleted && fileUrl != null) {
            // Storage에서 파일 삭제 후 Firestore 업데이트
            StorageReference fileRef = storage.getReferenceFromUrl(fileUrl);
            fileRef.delete()
                    .addOnSuccessListener(aVoid -> {
                        fileUrl = null; // 파일 URL을 null로 설정하여 Firestore에서 제거
                        fileName = null; // 파일 이름도 null로 설정
                        saveUpdatedPost(updatedTitle, updatedContent, null, null);
                        Toast.makeText(this, "파일이 성공적으로 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "파일 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    });
        } else if (fileUri != null) {
            // 새로운 파일이 선택된 경우 기존 파일 삭제 후 새 파일 업로드
            if (fileUrl != null && !fileUrl.isEmpty()) {
                StorageReference oldFileRef = storage.getReferenceFromUrl(fileUrl);
                oldFileRef.delete().addOnSuccessListener(aVoid -> {
                    uploadNewFileAndSavePost(updatedTitle, updatedContent); // 기존 파일 삭제 후 새 파일 업로드
                }).addOnFailureListener(e -> {
                    Toast.makeText(this, "기존 파일 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
            } else {
                uploadNewFileAndSavePost(updatedTitle, updatedContent); // 기존 파일이 없을 경우 새 파일 업로드만 수행
            }
        } else {
            // 파일이 수정되지 않은 경우 Firestore에 제목과 내용만 업데이트
            saveUpdatedPost(updatedTitle, updatedContent, fileName, fileUrl);
        }
    }


    private void saveUpdatedPost(String title, String content, String fileName, String fileUrl) {
        db.collection("posts").document(documentId)
                .update("title", title, "content", content, "fileName", fileName, "fileUrl", fileUrl)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "글이 수정되었습니다.", Toast.LENGTH_SHORT).show();
                    finish(); // 수정 후 액티비티 종료
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "글 수정에 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
    }

    private void uploadNewFileAndSavePost(String title, String content) {
        fileName = fileUri.getLastPathSegment();
        String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName;
        StorageReference newFileRef = storage.getReference().child("uploads/" + uniqueFileName);

        newFileRef.putFile(fileUri)
                .addOnSuccessListener(taskSnapshot -> newFileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String newFileUrl = uri.toString();

                    // Firestore에 새로운 파일 정보와 함께 업데이트
                    db.collection("posts").document(documentId)
                            .update("title", title, "content", content, "fileName", fileName, "fileUrl", newFileUrl)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "글과 파일이 수정되었습니다.", Toast.LENGTH_SHORT).show();
                                finish(); // 수정 후 액티비티 종료
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "글 수정에 실패했습니다.", Toast.LENGTH_SHORT).show();
                            });
                }))
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "새 파일 업로드에 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
    }


    private void deletePost() {
        // Firebase Storage에서 파일 삭제
        if (fileUrl != null && !fileUrl.isEmpty()) {
            StorageReference fileRef = storage.getReferenceFromUrl(fileUrl);
            fileRef.delete()
                    .addOnSuccessListener(aVoid -> deletePostFromFirestore())
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "파일 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    });
        } else {
            deletePostFromFirestore();
        }
    }

    private void deletePostFromFirestore() {
        // Firestore에서 글 삭제
        db.collection("posts").document(documentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "글이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                    finish(); // 삭제 후 액티비티 종료
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "글 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
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
//                    Toast.makeText(MyExistTextActivity.this, "글이 성공적으로 저장되었습니다.", Toast.LENGTH_SHORT).show();
//                    finish();
//                })
//                .addOnFailureListener(e -> Toast.makeText(MyExistTextActivity.this, "글 저장에 실패했습니다.", Toast.LENGTH_SHORT).show());
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

}
