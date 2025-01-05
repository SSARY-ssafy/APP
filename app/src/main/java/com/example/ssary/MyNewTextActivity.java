package com.example.ssary;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
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

import com.example.ssary.ui.theme.UndoRedoManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;


public class MyNewTextActivity extends AppCompatActivity {

    private EditText titleEditText, contentEditText;
    private LinearLayout uploadedFileContainer;
    private ImageView boldButton, italicButton, underlineButton, strikethroughButton, imageButton, undoButton, redoButton;
    private UndoRedoManager undoRedoManager;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private final AtomicInteger completedTasks = new AtomicInteger(0);
    private final int totalTasks = 2;
    private List<Map<String, String>> imageData = new ArrayList<>();
    private List<Map<String, String>> fileData = new ArrayList<>();

    private final List<Uri> fileUris = new ArrayList<>();
    private final List<String> fileNames = new ArrayList<>();
    private final List<Uri> imageUris = new ArrayList<>();
    private final List<String> imageNames = new ArrayList<>();
    private final List<Integer> imagePositions = new ArrayList<>();
    private Spinner categorySpinner;
    private ArrayList<String> categoryList;
    private ArrayAdapter<String> categoryAdapter;

    private boolean isUndoRedoAction = false;
    private boolean isBold = false, isItalic = false, isUnderline = false, isStrikethrough = false;
    private ImageSpan deletedImageSpan;
    private boolean isImageInsertionInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_new_text);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance("gs://ssary-83359");

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
        undoButton = findViewById(R.id.undoButton);
        redoButton = findViewById(R.id.redoButton);
        undoRedoManager = new UndoRedoManager();

        // 초기화 코드
        loadInitialState(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

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


        submitPostButton.setOnClickListener(v -> submitPost());
        uploadFileButton.setOnClickListener(v -> selectFiles());

        setupButtonListeners();
        setupTextWatcher();

        // 텍스트 변경 이벤트 처리
        contentEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyCurrentStyleToInput();
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateImagePositions(s);

                // Undo/Redo 중이 아니고 이미지 삭제 액션일 때만 다이얼로그 호출
                if (deletedImageSpan != null && !isUndoRedoAction) {
                    Uri imageUri = Uri.parse(deletedImageSpan.getSource());
                    showDeleteImageDialog(imageUri);
                    deletedImageSpan = null;
                }
                if (!isUndoRedoAction) {
                    // UndoRedoManager에 현재 상태 저장
                    undoRedoManager.saveState(new UndoRedoManager.State(
                            new SpannableString(s),
                            new ArrayList<>(imageUris),
                            new ArrayList<>(imageNames),
                            new ArrayList<>(imagePositions)
                    ));
                    updateUndoRedoButtons(); // 버튼 활성화 상태 업데이트
                }
            }
        });

        // Undo 버튼 클릭 리스너
        undoButton.setOnClickListener(v -> {
            if (undoRedoManager.canUndo()) {
                isUndoRedoAction = true;
                UndoRedoManager.State previousState = undoRedoManager.undo();

                // 상태 복원
                restoreState(previousState);

                isUndoRedoAction = false;
            }
            updateUndoRedoButtons();
        });

        // Redo 버튼 클릭 리스너
        redoButton.setOnClickListener(v -> {
            if (undoRedoManager.canRedo()) {
                isUndoRedoAction = true;
                UndoRedoManager.State nextState = undoRedoManager.redo();

                // 상태 복원
                restoreState(nextState);

                isUndoRedoAction = false;
            }
            updateUndoRedoButtons();
        });

        // 버튼 초기 상태 설정
        updateUndoRedoButtons();
    }

    private void updateImagePositions(Editable text) {
        if (isImageInsertionInProgress) return;  // 이미지 삽입 중이면 업데이트 중단

        // 모든 ImageSpan 탐색
        ImageSpan[] spans = text.getSpans(0, text.length(), ImageSpan.class);

        // 새로 탐색한 이미지 정보 임시 저장
        List<Integer> newImagePositions = new ArrayList<>();
        List<Uri> newImageUris = new ArrayList<>();
        List<String> newImageNames = new ArrayList<>();

        for (ImageSpan span : spans) {
            String source = span.getSource();
            if (source == null) continue;

            int start = text.getSpanStart(span);
            Uri imageUri = Uri.parse(source);

            if (imageUris.contains(imageUri)) {
                int index = imageUris.indexOf(imageUri);
                if (index != -1) {
                    newImagePositions.add(start);
                    newImageUris.add(imageUris.get(index));
                    newImageNames.add(imageNames.get(index));
                }
            }
        }

        // 기존 이미지 정보 업데이트
        imagePositions.clear();
        imageUris.clear();
        imageNames.clear();
        imagePositions.addAll(newImagePositions);
        imageUris.addAll(newImageUris);
        imageNames.addAll(newImageNames);
    }

    // 이미지 삭제 대화상자 표시 메서드
    private void showDeleteImageDialog(Uri imageUri) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("이미지 삭제")
                .setMessage("해당 이미지를 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialogInterface, which) -> deleteImage(imageUri))
                .setNegativeButton("취소", (dialogInterface, which) -> {
                    if (undoRedoManager.canUndo()) {
                        isUndoRedoAction = true;
                        UndoRedoManager.State previousState = undoRedoManager.undo();
                        restoreState(previousState);
                        isUndoRedoAction = false;
                    }
                    updateUndoRedoButtons();
                })
                .create();

        // 다이얼로그가 외부 터치나 뒤로가기 버튼으로 닫히지 않도록 설정
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        // 다이얼로그 표시
        dialog.show();
    }

    private void deleteImage(Uri imageUri) {
        int index = imageUris.indexOf(imageUri);
        if (index != -1) {
            imageUris.remove(index);
            imageNames.remove(index);
            imagePositions.remove(index);
        }
    }

    private void submitPost() {
        String postTitle = titleEditText.getText().toString().trim();
        String postContent = contentEditText.getText().toString().trim();
        String category = categorySpinner.getSelectedItem().toString();

        if (category.equals("카테고리 선택")) {
            Toast.makeText(MyNewTextActivity.this, "카테고리를 선택해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (postTitle.isEmpty() || postContent.isEmpty()) {
            Toast.makeText(MyNewTextActivity.this, "제목과 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        completedTasks.set(0);
        imageData.clear();
        fileData.clear();

        if (!imageUris.isEmpty()) {
            uploadImagesToStorage(category, postTitle);
        } else {
            completedTasks.incrementAndGet();
        }

        if (!fileUris.isEmpty()) {
            uploadFilesToStorage(category, postTitle);
        } else {
            completedTasks.incrementAndGet();
        }

        checkAndSavePost(category, postTitle);
    }

    // undo redo 초기화
    private void loadInitialState(List<Uri> initialImageUris, List<String> initialImageNames, List<Integer> initialImagePositions) {
        contentEditText.setText("");
        imageUris.addAll(initialImageUris);
        imageNames.addAll(initialImageNames);
        imagePositions.addAll(initialImagePositions);

        // UndoRedoManager에 초기 상태 저장
        undoRedoManager.saveState(new UndoRedoManager.State(
                new SpannableString(""),
                imageUris,
                imageNames,
                imagePositions,
                contentEditText.getSelectionStart()
        ));

        updateUndoRedoButtons(); // 초기 버튼 상태 업데이트
    }

    // Undo/Redo 버튼 활성화 상태 업데이트
    private void updateUndoRedoButtons() {
        undoButton.setEnabled(undoRedoManager.canUndo());
        redoButton.setEnabled(undoRedoManager.canRedo());
        undoButton.setAlpha(undoRedoManager.canUndo() ? 1.0f : 0.5f);
        redoButton.setAlpha(undoRedoManager.canRedo() ? 1.0f : 0.5f);
    }

    // ActivityResultLauncher를 등록하여 이미지를 선택한 결과를 처리
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    insertImageAtCursor(imageUri);
                }
            }
    );

    // 선택한 이미지를 contentEditText의 현재 커서 위치에 삽입
    private void insertImageAtCursor(Uri imageUri) {
        try {
            isImageInsertionInProgress = true;  // 이미지 삽입 시작 플래그 설정

            Drawable drawable = Drawable.createFromStream(
                    getContentResolver().openInputStream(imageUri),
                    null
            );
            assert drawable != null;
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());

            // 커서 위치에 이미지 추가
            int position = contentEditText.getSelectionEnd();
            Editable text = contentEditText.getText();
            ImageSpan imageSpan = new ImageSpan(drawable, ImageSpan.ALIGN_BASELINE);
            text.insert(position, " "); // 이미지 자리 확보
            text.setSpan(imageSpan, position, position + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            // 이미지 데이터 업데이트
            imageUris.add(imageUri);
            imageNames.add(getFileName(imageUri));
            imagePositions.add(position);

            // 상태 저장
            undoRedoManager.saveState(new UndoRedoManager.State(
                    new SpannableString(contentEditText.getText()),
                    imageUris,
                    imageNames,
                    imagePositions,
                    contentEditText.getSelectionStart()
            ));

            updateUndoRedoButtons();
        } catch (Exception e) {
            Toast.makeText(this, "이미지를 삽입할 수 없습니다.", Toast.LENGTH_SHORT).show();
        } finally {
            isImageInsertionInProgress = false;  // 이미지 삽입 종료 플래그 설정
        }
    }

    private void restoreState(UndoRedoManager.State state) {
        // 텍스트 복원
        contentEditText.setText(state.text);

        // 이미지 복원
        imageUris.clear();
        imageNames.clear();
        imagePositions.clear();

        imageUris.addAll(state.imageUris);
        imageNames.addAll(state.imageNames);
        imagePositions.addAll(state.imagePositions);

        // 커서 위치 조정
        contentEditText.setSelection(state.cursorPosition);
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
    private void savePostToDB(String category, String title,
                              List<Map<String, String>> fileData, List<Map<String, String>> imageData) {
        Map<String, Object> post = new HashMap<>();
        String htmlContent = convertToHtmlStyledContent();

        post.put("category", category);
        post.put("title", title);
        post.put("content", htmlContent);
        post.put("files", fileData);
        post.put("images", imageData);

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
    private void uploadFilesToStorage(String category, String title) {
        int totalFiles = fileUris.size();

        for (int i = 0; i < totalFiles; i++) {
            Uri fileUri = fileUris.get(i);
            String fileName = fileNames.get(i);
            String fileExtension = getFileExtension(fileUri);

            String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName;
            StorageReference fileRef = storage.getReference().child("files/" + uniqueFileName);

            fileRef.putFile(fileUri)
                    .addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        Map<String, String> fileInfo = new HashMap<>();
                        fileInfo.put("fileName", fileName);
                        fileInfo.put("fileExtension", fileExtension != null ? fileExtension : "unknown");
                        fileInfo.put("fileUrl", uri.toString());
                        fileData.add(fileInfo);

                        if (fileData.size() == totalFiles) {
                            completedTasks.incrementAndGet();
                            checkAndSavePost(category, title);
                        }
                    }))
                    .addOnFailureListener(e -> {
                        Toast.makeText(MyNewTextActivity.this, "파일 업로드 실패: " + fileName, Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void uploadImagesToStorage(String category, String title) {
        updateImagePositions(contentEditText.getText());

        int totalImages = imageUris.size();

        for (int i = 0; i < totalImages; i++) {
            Uri imageUri = imageUris.get(i);
            String imageName = imageNames.get(i);
            int imagePosition = imagePositions.get(i);
            String imageExtension = getFileExtension(imageUri);

            String uniqueImageName = UUID.randomUUID().toString() + "_" + imageName;
            StorageReference fileRef = storage.getReference().child("images/" + uniqueImageName);

            fileRef.putFile(imageUri)
                    .addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        Map<String, String> imageInfo = new HashMap<>();
                        imageInfo.put("imageName", imageName);
                        imageInfo.put("imageExtension", imageExtension != null ? imageExtension : "unknown");
                        imageInfo.put("imageUrl", uri.toString());
                        imageInfo.put("imagePosition", String.valueOf(imagePosition));
                        imageData.add(imageInfo);

                        if (imageData.size() == totalImages) {
                            completedTasks.incrementAndGet();
                            checkAndSavePost(category, title);
                        }
                    }))
                    .addOnFailureListener(e -> {
                        Toast.makeText(MyNewTextActivity.this, "이미지 업로드 실패: " + imageUri, Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void checkAndSavePost(String category, String title) {
        if (completedTasks.get() == totalTasks) {
            savePostToDB(category, title, fileData, imageData);
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

            String displayName = truncateFileName(fileName);
            fileTextView.setText(displayName);
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

    // 파일 이름을 원하는 길이로 줄이는 메서드
    private String truncateFileName(String fileName) {
        BreakIterator charIterator = BreakIterator.getCharacterInstance();
        charIterator.setText(fileName);
        int maxLength = 40;

        int endIndex = 0;
        int count = 0;

        while (charIterator.next() != BreakIterator.DONE) {
            if (++count > maxLength) break;
            endIndex = charIterator.current();
        }

        return count > maxLength ? fileName.substring(0, endIndex) + "..." : fileName;
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
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
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
    private String convertToHtmlStyledContent() {
        StringBuilder htmlContent = new StringBuilder();
        Editable text = contentEditText.getText();

        // 이미지 데이터 정렬 (위치 기준)
        imageData.sort(Comparator.comparingInt(a -> Integer.parseInt(a.get("imagePosition"))));

        int currentIndex = 0; // 텍스트의 현재 위치
        int imageIndex = 0; // 이미지 데이터의 현재 인덱스

        while (currentIndex < text.length() || imageIndex < imageData.size()) {
            int imagePosition = imageIndex < imageData.size()
                    ? Integer.parseInt(imageData.get(imageIndex).get("imagePosition"))
                    : Integer.MAX_VALUE;

            if (currentIndex < imagePosition) {
                // 텍스트 처리
                char ch = text.charAt(currentIndex++);
                if (ch == '\n') {
                    htmlContent.append("<br>"); // 개행 문자를 <br>로 변환
                } else {
                    htmlContent.append(processStyledCharacter(text, currentIndex - 1, ch));
                }
            } else {
                Map<String, String> imageInfo = imageData.get(imageIndex++);
                String imageUrl = imageInfo.get("imageUrl");
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    htmlContent.append("<img src=\"").append(imageUrl).append("\" />");
                }
            }
        }

        return htmlContent.toString();
    }

    private String processStyledCharacter(Editable text, int position, char ch) {
        StringBuilder result = new StringBuilder();

        // 스타일 확인
        boolean isBold = false, isItalic = false, isUnderline = false, isStrikethrough = false;

        for (StyleSpan span : text.getSpans(position, position + 1, StyleSpan.class)) {
            if (span.getStyle() == Typeface.BOLD) isBold = true;
            if (span.getStyle() == Typeface.ITALIC) isItalic = true;
        }
        if (text.getSpans(position, position + 1, UnderlineSpan.class).length > 0) isUnderline = true;
        if (text.getSpans(position, position + 1, StrikethroughSpan.class).length > 0) isStrikethrough = true;

        // HTML 스타일 태그 추가
        if (isBold) result.append("<b>");
        if (isItalic) result.append("<i>");
        if (isUnderline) result.append("<u>");
        if (isStrikethrough) result.append("<s>");

        // 텍스트 추가
        result.append(ch);

        // 닫는 태그 추가
        if (isStrikethrough) result.append("</s>");
        if (isUnderline) result.append("</u>");
        if (isItalic) result.append("</i>");
        if (isBold) result.append("</b>");

        return result.toString();
    }

}
