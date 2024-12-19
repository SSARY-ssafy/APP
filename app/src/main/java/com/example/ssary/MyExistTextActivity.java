package com.example.ssary;

import android.app.DownloadManager;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.Context;
import android.os.Environment;


import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class MyExistTextActivity extends AppCompatActivity {

    private EditText titleEditText, contentEditText;
    private LinearLayout uploadedFileContainer;
    private ImageView boldButton, italicButton, underlineButton, strikethroughButton, uploadFileButton, imageButton;
    private Button savePostButton, updatePostButton, deletePostButton;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private final List<Uri> fileUris = new ArrayList<>();
    private final List<String> fileExtensions = new ArrayList<>();
    private final List<String> fileNames = new ArrayList<>();
    private final List<Uri> deletedFileUris = new ArrayList<>();
    private final List<Uri> updatedFileUris = new ArrayList<>();
    private final List<String> updatedFileNames = new ArrayList<>();
    private boolean isEditing = false;

    private String documentId;
    private boolean isBold = false, isItalic = false, isUnderline = false, isStrikethrough = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_exist_text);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        titleEditText = findViewById(R.id.titleEditText);
        contentEditText = findViewById(R.id.contentEditText);
        uploadedFileContainer = findViewById(R.id.uploadedFileContainer);
        boldButton = findViewById(R.id.boldButton);
        italicButton = findViewById(R.id.italicButton);
        underlineButton = findViewById(R.id.underlineButton);
        strikethroughButton = findViewById(R.id.strikethroughButton);
        uploadFileButton = findViewById(R.id.uploadFileButton);

        updatePostButton = findViewById(R.id.updatePostButton);
        deletePostButton = findViewById(R.id.deletePostButton);
        savePostButton = findViewById(R.id.savePostButton);

        categorySpinner = findViewById(R.id.categorySpinner);
        categoryList = new ArrayList<>();
        categoryList.add("카테고리 선택");
        loadCategoriesFromDB();

        categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categoryList);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);

        Intent intent = getIntent();
        String selectedCategory = intent.getStringExtra("category");
        if (selectedCategory != null && !selectedCategory.isEmpty()) {
            int position = categoryList.indexOf(selectedCategory);
            if (position >= 0) {
                categorySpinner.setSelection(position);
            }
        }

        documentId = intent.getStringExtra("documentId");
        enableEditing(false);
        loadPostFromDB(documentId);

        savePostButton.setOnClickListener(v -> updatePost());
        updatePostButton.setOnClickListener(v -> {
            enableEditing(true);

            updateTitleEditTextConstraint(savePostButton.getId());
        });
        deletePostButton.setOnClickListener(v -> deletePost());
        uploadFileButton.setOnClickListener(v -> selectFiles());

        setupButtonListeners();
        setupTextWatcher();
    }

    private void updateTitleEditTextConstraint(int topToBottomOfId) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) titleEditText.getLayoutParams();
        params.topToBottom = topToBottomOfId;
        titleEditText.setLayoutParams(params);
    }

    private void enableEditing(boolean isEditable) {
        isEditing = isEditable;
        titleEditText.setEnabled(isEditable);
        contentEditText.setEnabled(isEditable);

        LinearLayout formattingButtonContainer = findViewById(R.id.formattingButtonsContainer);
        View topHrView = findViewById(R.id.topHrView);
        View bottomHrView = findViewById(R.id.bottomHrView);

        if (isEditable) {
            uploadFileButton.setVisibility(View.VISIBLE);
            savePostButton.setVisibility(View.VISIBLE);
            updatePostButton.setVisibility(View.GONE);
            deletePostButton.setVisibility(View.GONE);

            formattingButtonContainer.setVisibility(View.VISIBLE);
            topHrView.setVisibility(View.VISIBLE);
            bottomHrView.setVisibility(View.VISIBLE);
        } else {
            uploadFileButton.setVisibility(View.GONE);
            savePostButton.setVisibility(View.GONE);
            updatePostButton.setVisibility(View.VISIBLE);
            deletePostButton.setVisibility(View.VISIBLE);

            formattingButtonContainer.setVisibility(View.GONE);
            topHrView.setVisibility(View.GONE);
            bottomHrView.setVisibility(View.GONE);
        }

        updateUploadedFilesUI();
    }

    private void downloadFile(Uri fileUri, String fileName) {
        if (fileUri != null) {
            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(fileUri);
            request.setTitle(fileName);
            request.setDescription("파일 다운로드 중...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            String mimeType = getMimeType(fileExtension);
            request.setMimeType(mimeType);

            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            downloadManager.enqueue(request);

            Toast.makeText(this, "이미지 다운로드를 시작합니다: " + fileName, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "다운로드할 파일이 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private String getMimeType(String fileExtension) {
        switch (fileExtension) {
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "pdf": return "application/pdf";
            case "txt": return "text/plain";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default: return "*/*";
        }
    }

    private void selectFiles() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        filePickerLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    handleFileSelection(result.getData());
                }
            }
    );

    private void handleFileSelection(Intent data) {
        if (data.getClipData() != null) {
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count; i++) {
                Uri updatedFileUri = data.getClipData().getItemAt(i).getUri();
                String updatedFileName = getFileName(updatedFileUri);
                updatedFileUris.add(updatedFileUri);
                updatedFileNames.add(updatedFileName != null ? updatedFileName : "파일 이름 없음");
            }
        } else if (data.getData() != null) {
            Uri updatedFileUri = data.getData();
            String updatedFileName = getFileName(updatedFileUri);
            updatedFileUris.add(updatedFileUri);
            updatedFileNames.add(updatedFileName != null ? updatedFileName : "파일 이름 없음");
        }

        updateUploadedFilesUI();
    }

    private String getFileName(Uri uri) {
        String fileName = null;
        if (Objects.equals(uri.getScheme(), "content")) {
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

    private void loadPostFromDataBase(String documentId) {
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
        db.collection("posts").document(documentId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String title = documentSnapshot.getString("title");
                        String styledContent = documentSnapshot.getString("content");
                        titleEditText.setText(title);

                        if (styledContent != null && !styledContent.isEmpty()) {
                            CharSequence styledText = parseHtmlContent(styledContent);
                            contentEditText.setText(styledText);
                        } else {
                            contentEditText.setText("NONE");
                        }

                        List<Map<String, String>> files = (List<Map<String, String>>) documentSnapshot.get("files");
                        if (files != null) {
                            fileUris.clear();
                            fileExtensions.clear();
                            fileNames.clear();

                            for (Map<String, String> file : files) {
                                fileUris.add(Uri.parse(file.get("fileUrl")));
                                fileExtensions.add(file.get("fileExtension"));
                                fileNames.add(file.get("fileName"));
                            }

                            updateUploadedFilesUI();
                        }
                    } else {
                        Toast.makeText(this, "글 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "글을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateUploadedFilesUI() {
        uploadedFileContainer.removeAllViews();
        addFilesToContainer(fileUris, fileNames, false);
        addFilesToContainer(updatedFileUris, updatedFileNames, true);
        uploadedFileContainer.setVisibility(
                fileUris.isEmpty() && updatedFileUris.isEmpty() ? View.GONE : View.VISIBLE
        );
    }

    private void addFilesToContainer(List<Uri> uris, List<String> names, boolean isNewFiles) {
        for (int i = 0; i < uris.size(); i++) {
            String fileName = names.get(i);
            Uri fileUri = uris.get(i);
            int finalI = i;

            View fileItemView = LayoutInflater.from(this).inflate(R.layout.my_exist_text_uploaded_file, uploadedFileContainer, false);

            TextView fileTextView = fileItemView.findViewById(R.id.uploadedFileTextView);
            fileTextView.setText(fileName);
            fileTextView.setOnClickListener(v -> openFile(fileUri));

            ImageView downloadFileButton = fileItemView.findViewById(R.id.downloadFileButton);
            downloadFileButton.setOnClickListener(v -> downloadFile(fileUri, fileName));

            ImageView deleteFileButton = fileItemView.findViewById(R.id.deleteFileButton);
            if (isEditing) {
                deleteFileButton.setVisibility(View.VISIBLE);
                deleteFileButton.setOnClickListener(v -> {
                    new AlertDialog.Builder(this)
                            .setTitle("파일 삭제 확인")
                            .setMessage("정말 이 파일을 삭제하시겠습니까?")
                            .setPositiveButton("삭제", (dialog, which) -> {
                                if (isNewFiles) {
                                    updatedFileUris.remove(finalI);
                                    updatedFileNames.remove(finalI);
                                } else {
                                    deletedFileUris.add(existedFileUris.remove(finalI));
                                    existedFileExtensions.remove(finalI);
                                    existedFileNames.remove(finalI);
                                }
                                updateUploadedFilesUI();
                                Toast.makeText(this, "파일이 삭제되었습니다: " + fileName, Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("취소", (dialog, which) -> {})
                            .show();
                });
            } else {
                deleteFileButton.setVisibility(View.GONE);
            }

            uploadedFileContainer.addView(fileItemView);
        }
    }

    private CharSequence parseHtmlContent(String htmlContent) {
        return Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY);
    }

    private void openFile(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void updatePost() {
        String updatedTitle = titleEditText.getText().toString().trim();
        String updatedContents = contentEditText.getText().toString().trim();

        String updatedContent = convertToHtmlStyledContent(updatedContents);

        if (updatedTitle.isEmpty() || updatedContent.isEmpty()) {
            Toast.makeText(this, "제목과 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!deletedFileUris.isEmpty()) {
            deleteFilesFromStorage(() -> uploadNewFileAndUpdatePost(updatedTitle, updatedContent));
        } else {
            uploadNewFileAndUpdatePost(updatedTitle, updatedContent);
        }

    }

    private void deleteFilesFromStorage(Runnable onComplete) {
        if (deletedFileUris.isEmpty()) {
            onComplete.run();
            return;
        }

        final List<StorageReference> fileReferences = new ArrayList<>();
        for (Uri fileUri : deletedFileUris) {
            fileReferences.add(storage.getReferenceFromUrl(fileUri.toString()));
        }

        final int totalFiles = fileReferences.size();
        final int[] completedFiles = {0};
        final boolean[] hasErrorOccurred = {false};

        for (StorageReference fileRef : fileReferences) {
            fileRef.delete()
                    .addOnSuccessListener(aVoid -> {
                        completedFiles[0]++;
                        if (completedFiles[0] == totalFiles && !hasErrorOccurred[0]) {
                            deletedFileUris.clear();
                            onComplete.run();
                        }
                    })
                    .addOnFailureListener(e -> {
                        hasErrorOccurred[0] = true; // 에러 발생
                        Toast.makeText(this, "파일 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void saveUpdatedPost(String title, String content, List<Map<String, String>> files) {
        db.collection("posts").document(documentId)
                .update("title", title, "content", content, "files", files)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "글이 수정되었습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "글 수정에 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
    }

    private void uploadNewFileAndUpdatePost(String title, String content) {
        final List<Map<String, String>> updatedFiles = new ArrayList<>();
        int totalUpdatedFiles = updatedFileUris.size();
        final int[] completedFiles = {0};

        if(!fileUris.isEmpty()) {
            for (int i = 0; i < fileUris.size(); i++) {
                Map<String, String> fileInfo = new HashMap<>();
                fileInfo.put("fileName", fileNames.get(i));
                fileInfo.put("fileExtension", fileExtensions.get(i));
                fileInfo.put("fileUrl", fileUris.get(i).toString());
                updatedFiles.add(fileInfo);
            }
        }

        for (int i = 0; i < totalUpdatedFiles; i++) {
            Uri updatedFileUri = updatedFileUris.get(i);
            if (!isLocalUri(updatedFileUri)) {
                Toast.makeText(this, "유효하지 않은 파일 URI: " + updatedFileUri, Toast.LENGTH_SHORT).show();
                continue;
            }

            String updatedFileName = updatedFileNames.get(i);
            String updatedFileExtension = getFileExtension(updatedFileUri);
            String uniqueFileName = UUID.randomUUID().toString() + "_" + updatedFileName;
            StorageReference fileRef = storage.getReference().child("uploads/" + uniqueFileName);

            fileRef.putFile(updatedFileUri)
                    .addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        Map<String, String> fileInfo = new HashMap<>();
                        fileInfo.put("fileName", updatedFileName);
                        fileInfo.put("fileExtension", updatedFileExtension != null ? updatedFileExtension : "unknown");
                        fileInfo.put("fileUrl", uri.toString());
                        updatedFiles.add(fileInfo);

                        completedFiles[0]++;
                        if (completedFiles[0] == totalUpdatedFiles) {
                            saveUpdatedPost(title, content, updatedFiles);
                        }
                    }))
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "파일 업로드 실패: " + updatedFileName, Toast.LENGTH_SHORT).show();
                    });
        }

        if (totalUpdatedFiles == 0) {
            saveUpdatedPost(title, content, updatedFiles);
        }
    }

    private boolean isLocalUri(Uri uri) {
        String scheme = uri.getScheme();
        return scheme != null && (scheme.equals("content") || scheme.equals("file"));
    }

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

    private void deletePost() {
        new AlertDialog.Builder(this)
                .setTitle("삭제 확인")
                .setMessage("정말 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, which) -> {
                    if (!existedFileUris.isEmpty()) {
                        deletedFileUris.addAll(existedFileUris);
                        deleteFilesFromStorage(this::deletePostFromDB);
                    } else {
                        deletePostFromDB();
                    }
                })
                .setNegativeButton("취소", (dialog, which) -> {})
                .show();
    }

    private void deletePostFromDataBase() {
        db.collection("posts").document(documentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "글이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "글 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
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

            for (StyleSpan span : text.getSpans(i, i + 1, StyleSpan.class)) {
                if (span.getStyle() == Typeface.BOLD) bold = true;
                if (span.getStyle() == Typeface.ITALIC) italic = true;
            }
            if (text.getSpans(i, i + 1, UnderlineSpan.class).length > 0) underline = true;
            if (text.getSpans(i, i + 1, StrikethroughSpan.class).length > 0) strikethrough = true;

            if (isBold && !bold) htmlContent.append("</b>");
            if (isItalic && !italic) htmlContent.append("</i>");
            if (isUnderline && !underline) htmlContent.append("</u>");
            if (isStrikethrough && !strikethrough) htmlContent.append("</s>");

            if (!isBold && bold) htmlContent.append("<b>");
            if (!isItalic && italic) htmlContent.append("<i>");
            if (!isUnderline && underline) htmlContent.append("<u>");
            if (!isStrikethrough && strikethrough) htmlContent.append("<s>");

            htmlContent.append(ch);

            isBold = bold;
            isItalic = italic;
            isUnderline = underline;
            isStrikethrough = strikethrough;
        }

        if (isStrikethrough) htmlContent.append("</s>");
        if (isUnderline) htmlContent.append("</u>");
        if (isItalic) htmlContent.append("</i>");
        if (isBold) htmlContent.append("</b>");

        return htmlContent.toString();
    }

}
