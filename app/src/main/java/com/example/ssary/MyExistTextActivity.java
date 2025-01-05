/* 로직
해당 글 클릭 시 -> loadPostFromDB() -> updateUploadedFilesUI() -> addFilesToContainer()
해당 글 저장 버튼 클릭 -> updatePost() -> deleteFilesFromStorage() -> uploadNewFileAndUpdatePost() -> saveUpdatedPost()
해당 글 삭제 버튼 클릭 -> deletePost() -> deleteFilesFromStorage() or deletePostFromDB()
 */

package com.example.ssary;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.Context;
import android.os.Environment;

import com.example.ssary.ui.theme.UndoRedoManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.net.URL;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class MyExistTextActivity extends AppCompatActivity {

    private EditText titleEditText, contentEditText;
    private LinearLayout uploadedFileContainer;
    private ImageView boldButton, italicButton, underlineButton, strikethroughButton, uploadFileButton, imageButton, undoButton, redoButton;
    private UndoRedoManager undoRedoManager;
    private Button submitPostButton, updatePostButton, deletePostButton;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private final AtomicInteger completedTasks = new AtomicInteger(0);
    private final int totalTasks = 4;
    private final AtomicInteger deletedTasks = new AtomicInteger(0);
    private final int totaldeleteTasks = 2;
    private List<Map<String, String>> imageData = new ArrayList<>();
    private List<Map<String, String>> fileData = new ArrayList<>();

    private final List<Uri> curImageUris = new ArrayList<>();
    private final List<String> curImageNames = new ArrayList<>();
    private final List<Integer> curImagePositions = new ArrayList<>();

    private final List<Uri> existedImageUris = new ArrayList<>();
    private final List<String> existedImageNames = new ArrayList<>();
    private final List<Integer> existedImagePositions = new ArrayList<>();

    private final List<Uri> deletedImageUris = new ArrayList<>();

    private final List<Uri> updatedImageUris = new ArrayList<>();
    private final List<String> updatedImageNames = new ArrayList<>();
    private final List<Integer> updatedImagePositions = new ArrayList<>();

    private final List<Uri> existedFileUris = new ArrayList<>();
    private final List<String> existedFileExtensions = new ArrayList<>();
    private final List<String> existedFileNames = new ArrayList<>();

    private final List<Uri> deletedFileUris = new ArrayList<>();

    private final List<Uri> updatedFileUris = new ArrayList<>();
    private final List<String> updatedFileNames = new ArrayList<>();

    private boolean isEditing = false;
    private Spinner categorySpinner;
    private ArrayList<String> categoryList;
    private ArrayAdapter<String> categoryAdapter;

    private String documentId;
    private boolean isUndoRedoAction = false;
    private boolean isInitialAccess = true;
    private boolean isBold = false, isItalic = false, isUnderline = false, isStrikethrough = false;
    private ImageSpan deletedImageSpan;
    private boolean isImageInsertionInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_exist_text);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance("gs://ssary-83359");

        titleEditText = findViewById(R.id.titleEditText);
        contentEditText = findViewById(R.id.contentEditText);
        uploadedFileContainer = findViewById(R.id.uploadedFileContainer);
        boldButton = findViewById(R.id.boldButton);
        italicButton = findViewById(R.id.italicButton);
        underlineButton = findViewById(R.id.underlineButton);
        strikethroughButton = findViewById(R.id.strikethroughButton);
        uploadFileButton = findViewById(R.id.uploadFileButton);

        imageButton = findViewById(R.id.imageButton);
        undoButton = findViewById(R.id.undoButton);
        redoButton = findViewById(R.id.redoButton);
        undoRedoManager = new UndoRedoManager();

        updatePostButton = findViewById(R.id.updatePostButton);
        deletePostButton = findViewById(R.id.deletePostButton);
        submitPostButton = findViewById(R.id.savePostButton);

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

        documentId = getIntent().getStringExtra("documentId");
        enableEditing(false);
        loadPostFromDB(documentId);

        submitPostButton.setOnClickListener(v -> submitPost());
        uploadFileButton.setOnClickListener(v -> selectFiles());

        updatePostButton.setOnClickListener(v -> {
            enableEditing(true);
            updateTitleEditTextConstraint(submitPostButton.getId());
        });
        deletePostButton.setOnClickListener(v -> deletePost());

        setupButtonListeners();
        setupTextWatcher();

        // 텍스트 변경 이벤트 처리
        contentEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 삭제(백스페이스) 동작일 때만 이미지 스팬을 감지
                if (count > 0 && after == 0) {
                    ImageSpan[] spans = ((Editable) s).getSpans(start, start + count, ImageSpan.class);
                    if (spans.length > 0) {
                        deletedImageSpan = spans[0];
                    } else {
                        deletedImageSpan = null;
                    }
                } else {
                    deletedImageSpan = null;
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateImagePositions(s);

                // Undo/Redo 중이 아니고 이미지 삭제 액션일 때만 다이얼로그 호출
                if (deletedImageSpan != null && !isUndoRedoAction) {
                    Uri imageUri = Uri.parse(deletedImageSpan.getSource());
                    showDeleteImageDialog(imageUri);
                    deletedImageSpan = null;
                }

                if (!isUndoRedoAction && !isInitialAccess) {
                    // 이전 상태의 텍스트와 현재 텍스트 비교
                    CharSequence currentStateText = undoRedoManager.getCurrentStateText();
                    SpannableString newText = new SpannableString(s);

                    if ((currentStateText == null || !currentStateText.toString().equals(newText.toString()))) {
                        // UndoRedoManager에 현재 상태 저장
                        undoRedoManager.saveState(new UndoRedoManager.State(
                                newText,
                                new ArrayList<>(curImageUris),
                                new ArrayList<>(curImageNames),
                                new ArrayList<>(curImagePositions),
                                contentEditText.getSelectionStart()
                        ));
                        updateUndoRedoButtons();
                    }
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
        List<Integer> newExistedImagePositions = new ArrayList<>();
        List<Uri> newExistedImageUris = new ArrayList<>();
        List<String> newExistedImageNames = new ArrayList<>();

        List<Integer> newUpdatedImagePositions = new ArrayList<>();
        List<Uri> newUpdatedImageUris = new ArrayList<>();
        List<String> newUpdatedImageNames = new ArrayList<>();

        for (ImageSpan span : spans) {
            String source = span.getSource();
            if (source == null) continue;  // source가 null이면 건너뜀

            int start = text.getSpanStart(span);
            Uri imageUri = Uri.parse(source);

            if (existedImageUris.contains(imageUri)) {
                // 기존 이미지인 경우
                int index = existedImageUris.indexOf(imageUri);
                if (index != -1) {
                    newExistedImagePositions.add(start);
                    newExistedImageUris.add(existedImageUris.get(index));
                    newExistedImageNames.add(existedImageNames.get(index));
                }
            } else if (updatedImageUris.contains(imageUri)) {
                // 새로 추가된 이미지인 경우
                int index = updatedImageUris.indexOf(imageUri);
                if (index != -1) {
                    newUpdatedImagePositions.add(start);
                    newUpdatedImageUris.add(updatedImageUris.get(index));
                    newUpdatedImageNames.add(updatedImageNames.get(index));
                }
            }
        }

        // 기존 이미지 정보 업데이트
        existedImagePositions.clear();
        existedImageUris.clear();
        existedImageNames.clear();
        existedImagePositions.addAll(newExistedImagePositions);
        existedImageUris.addAll(newExistedImageUris);
        existedImageNames.addAll(newExistedImageNames);

        // 업데이트된 이미지 정보 업데이트
        updatedImagePositions.clear();
        updatedImageUris.clear();
        updatedImageNames.clear();
        updatedImagePositions.addAll(newUpdatedImagePositions);
        updatedImageUris.addAll(newUpdatedImageUris);
        updatedImageNames.addAll(newUpdatedImageNames);
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
        int index = curImageUris.indexOf(imageUri);
        if (index != -1) {
            curImageUris.remove(index);
            curImageNames.remove(index);
            curImagePositions.remove(index);

            // 삭제된 이미지가 existedImageUris에 있다면 삭제 관리
            if (existedImageUris.contains(imageUri)) {
                int existedIndex = existedImageUris.indexOf(imageUri);
                existedImageUris.remove(existedIndex);
                existedImageNames.remove(existedIndex);
                existedImagePositions.remove(existedIndex);

                deletedImageUris.add(imageUri);
            }
        }
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

            // 이미지 Drawable 생성
            Drawable drawable = Drawable.createFromStream(
                    getContentResolver().openInputStream(imageUri),
                    null
            );
            assert drawable != null;
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());

            // 커서 위치에 이미지 추가
            int position = contentEditText.getSelectionEnd();
            Editable text = contentEditText.getText();

            // 객체 치환 문자 삽입
            text.insert(position, " ");
            ImageSpan imageSpan = new ImageSpan(drawable, imageUri.toString(), ImageSpan.ALIGN_BASELINE);
            text.setSpan(imageSpan, position, position + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            // 변경된 텍스트를 EditText에 반영
            contentEditText.setText(text);

            // 커서를 이미지 뒤로 이동
            contentEditText.setSelection(position + 1);

            // 업데이트 이미지 정보 저장
            updatedImageUris.add(imageUri);
            updatedImageNames.add(getFileName(imageUri));
            updatedImagePositions.add(position);

            // 기존 + 업데이트 이미지 정보 저장
            curImageUris.add(imageUri);
            curImageNames.add(getFileName(imageUri));
            curImagePositions.add(position);

            // 상태 저장
            undoRedoManager.saveState(new UndoRedoManager.State(
                    new SpannableString(contentEditText.getText()),
                    curImageUris,
                    curImageNames,
                    curImagePositions,
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
        curImageUris.clear();
        curImageNames.clear();
        curImagePositions.clear();

        curImageUris.addAll(state.imageUris);
        curImageNames.addAll(state.imageNames);
        curImagePositions.addAll(state.imagePositions);

        // 이미지 상태 복원
        manageDeletedAndExistedImages(state);

        // 커서 위치 조정
        contentEditText.setSelection(state.cursorPosition);
    }

    // Undo & Redo 시, 이미지 삭제 및 추가 메서드
    private void manageDeletedAndExistedImages(UndoRedoManager.State state) {
        // Undo 시, 삭제된 이미지 관리
        for (Uri imageUri : existedImageUris) {
            if (!state.imageUris.contains(imageUri)) {
                // 삭제된 이미지 추가
                deletedImageUris.add(imageUri);

                int index = existedImageUris.indexOf(imageUri);
                if (index != -1) {
                    existedImageUris.remove(index);
                    existedImageNames.remove(index);
                    existedImagePositions.remove(index);
                }
            }
        }

        // Redo 시, 복구된 이미지 관리
        for (Uri imageUri : state.imageUris) {
            if (deletedImageUris.contains(imageUri)) {
                // 복구된 이미지 제거
                deletedImageUris.remove(imageUri);

                if (!existedImageUris.contains(imageUri)) {
                    existedImageUris.add(imageUri);
                    existedImageNames.add(state.imageNames.get(state.imageUris.indexOf(imageUri)));
                    existedImagePositions.add(state.imagePositions.get(state.imageUris.indexOf(imageUri)));
                }
            }
        }
    }

    // 수정 모드일 때, 제목의 위치 재설정
    private void updateTitleEditTextConstraint(int topToBottomOfId) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) titleEditText.getLayoutParams();
        params.topToBottom = topToBottomOfId;
        titleEditText.setLayoutParams(params);
    }

    // 수정 모드일 때, UI 설정
    private void enableEditing(boolean isEditable) {
        isEditing = isEditable;

        titleEditText.setEnabled(isEditable);
        contentEditText.setEnabled(isEditable);
        categorySpinner.setEnabled(isEditable);

        LinearLayout formattingButtonContainer = findViewById(R.id.formattingButtonsContainer);
        View topHrView = findViewById(R.id.topHrView);
        View bottomHrView = findViewById(R.id.bottomHrView);

        if (isEditable) {
            uploadFileButton.setVisibility(View.VISIBLE);
            submitPostButton.setVisibility(View.VISIBLE);
            updatePostButton.setVisibility(View.GONE);
            deletePostButton.setVisibility(View.GONE);

            formattingButtonContainer.setVisibility(View.VISIBLE);
            topHrView.setVisibility(View.VISIBLE);
            bottomHrView.setVisibility(View.VISIBLE);
        } else {
            uploadFileButton.setVisibility(View.GONE);
            submitPostButton.setVisibility(View.GONE);
            updatePostButton.setVisibility(View.VISIBLE);
            deletePostButton.setVisibility(View.VISIBLE);

            formattingButtonContainer.setVisibility(View.GONE);
            topHrView.setVisibility(View.GONE);
            bottomHrView.setVisibility(View.GONE);
        }

        updateUploadedFilesUI();
    }

    // 파일 다운로드 관련 로직
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

    // 다운로드에 필요한 확장자 설정 (MIME -> Multipurpose Internet Mail Extensions))
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

    // 새로 업데이트된 파일의 이름을 가져오는 메서드
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

    // DB로 부터 카테고리 목록을 가져오는 메서드
    private void loadCategoriesFromDB() {
        db.collection("categories")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
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

    // DB로 부터 글 정보(제목, 내용, 확장자)을 가져오는 메서드
    private void loadPostFromDB(String documentId) {
        db.collection("posts").document(documentId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String title = documentSnapshot.getString("title");
                        String content = documentSnapshot.getString("content");
                        titleEditText.setText(title);

                        CharSequence styledText = null;

                        if (content != null && !content.isEmpty()) {
                            styledText = parseHtmlContent(content);
                            contentEditText.setText(styledText);
                        } else {
                            contentEditText.setText("NONE");
                        }

                        List<Map<String, String>> files = (List<Map<String, String>>) documentSnapshot.get("files");
                        if (files != null) {
                            existedFileUris.clear();
                            existedFileExtensions.clear();
                            existedFileNames.clear();

                            for (Map<String, String> file : files) {
                                existedFileUris.add(Uri.parse(file.get("fileUrl")));
                                existedFileExtensions.add(file.get("fileExtension"));
                                existedFileNames.add(file.get("fileName"));
                            }

                            updateUploadedFilesUI();
                        }

                        List<Map<String, String>> images = (List<Map<String, String>>) documentSnapshot.get("images");
                        if (images != null) {
                            existedImageUris.clear();
                            existedImageNames.clear();
                            existedImagePositions.clear();

                            for (Map<String, String> image : images) {
                                existedImageUris.add(Uri.parse(image.get("imageUrl")));
                                existedImageNames.add(image.get("imageName"));
                                existedImagePositions.add(Integer.parseInt(image.get("imagePosition")));
                            }
                        }

                        loadInitialState(styledText, existedImageUris, existedImageNames, existedImagePositions);

                    } else {
                        Toast.makeText(this, "글 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "글을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadInitialState(CharSequence styledText, List<Uri> initialImageUris, List<String> initialImageNames, List<Integer> initialImagePositions) {
        curImageUris.clear();
        curImageNames.clear();
        curImagePositions.clear();

        curImageUris.addAll(initialImageUris);
        curImageNames.addAll(initialImageNames);
        curImagePositions.addAll(initialImagePositions);

        // UndoRedoManager에 초기 상태 저장
        if (styledText instanceof Spannable) {
            undoRedoManager.saveState(new UndoRedoManager.State(
                    (Spannable) styledText,
                    curImageUris,
                    curImageNames,
                    curImagePositions,
                    contentEditText.getSelectionStart()
            ));
        } else {
            Spannable spannableText = new SpannableString(styledText);
            undoRedoManager.saveState(new UndoRedoManager.State(
                    spannableText,
                    curImageUris,
                    curImageNames,
                    curImagePositions,
                    contentEditText.getSelectionStart()
            ));
        }

        isInitialAccess = false;

        updateUndoRedoButtons();
    }

    // 업로드된 파일 목록을 UI에 갱신
    private void updateUploadedFilesUI() {
        uploadedFileContainer.removeAllViews();
        addFilesToContainer(existedFileUris, existedFileNames, false);
        addFilesToContainer(updatedFileUris, updatedFileNames, true);
        uploadedFileContainer.setVisibility(
                existedFileUris.isEmpty() && updatedFileUris.isEmpty() ? View.GONE : View.VISIBLE
        );
    }

    // 파일 목록 컨테이너(UI)에 파일 추가
    private void addFilesToContainer(List<Uri> uris, List<String> names, boolean isNewFiles) {
        for (int i = 0; i < uris.size(); i++) {
            String fileName = names.get(i);
            Uri fileUri = uris.get(i);
            int finalI = i;

            View fileItemView = LayoutInflater.from(this).inflate(R.layout.my_exist_text_uploaded_file, uploadedFileContainer, false);

            TextView fileTextView = fileItemView.findViewById(R.id.uploadedFileTextView);

            String displayName = truncateFileName(fileName);
            fileTextView.setText(displayName);
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

    // HTML 콘텐츠를 Spannable로 변환
    private CharSequence parseHtmlContent(String htmlContent) {
        return Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY, source -> {
            final Drawable[] drawableWrapper = {null};

            Thread thread = new Thread(() -> {
                try {
                    Drawable drawable = Drawable.createFromStream(new URL(source).openStream(), null);
                    drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                    drawableWrapper[0] = drawable;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            thread.start();
            try {
                thread.join(); // 스레드 작업이 끝날 때까지 대기
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return drawableWrapper[0];
        }, null);
    }

    // 파일 이름을 클릭하면 파일을 열 수 있도록 Intent 실행
    private void openFile(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    // 게시글 수정 내용을 저장
    private void submitPost() {
        String updatedTitle = titleEditText.getText().toString().trim();
        String updatedContent = contentEditText.getText().toString().trim();
        String updatedCategory = categorySpinner.getSelectedItem().toString();

        if (updatedCategory.equals("카테고리 선택")) {
            Toast.makeText(MyExistTextActivity.this, "카테고리를 선택해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (updatedTitle.isEmpty() || updatedContent.isEmpty()) {
            Toast.makeText(this, "제목과 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        completedTasks.set(0);
        imageData.clear();
        fileData.clear();

        uploadNewImagesToStorage(updatedCategory, updatedTitle);

        if (!deletedImageUris.isEmpty()) {
            deleteImagesFromStorage(updatedCategory, updatedTitle);
        } else {
            completedTasks.incrementAndGet();
        }

        uploadNewFilesToStorage(updatedCategory, updatedTitle);

        if (!deletedFileUris.isEmpty()) {
            deleteFilesFromStorage(updatedCategory, updatedTitle);
        } else {
            completedTasks.incrementAndGet();
        }

        checkAndSavePost(updatedCategory, updatedTitle);
    }

    private void deleteImagesFromStorage(String category, String title) {
        final List<StorageReference> imageReferences = new ArrayList<>();
        for (Uri imageUri : deletedImageUris) {
            imageReferences.add(storage.getReferenceFromUrl(imageUri.toString()));
        }

        final int totalImages = imageReferences.size();
        final int[] completedImages = {0};

        for (StorageReference imageRef : imageReferences) {
            imageRef.delete()
                    .addOnSuccessListener(aVoid -> {
                        completedImages[0]++;
                            if (completedImages[0] == totalImages) {
                                completedTasks.incrementAndGet();
                                checkAndSavePost(category, title);
                            }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "이미지 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void deleteImagesFromStorageForDeletePost() {
        final List<StorageReference> imageReferences = new ArrayList<>();
        for (Uri imageUri : deletedImageUris) {
            imageReferences.add(storage.getReferenceFromUrl(imageUri.toString()));
        }

        final int totalImages = imageReferences.size();
        final int[] completedImages = {0};

        for (StorageReference imageRef : imageReferences) {
            imageRef.delete()
                    .addOnSuccessListener(aVoid -> {
                        completedImages[0]++;
                        if (completedImages[0] == totalImages) {
                            deletedTasks.incrementAndGet();
                            checkAndDeletePost();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "이미지 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void uploadNewImagesToStorage(String category, String title) {
        updateImagePositions(contentEditText.getText());

        if (!existedImageUris.isEmpty()) {
            for (int i = 0; i < existedImageUris.size(); i++) {
                Map<String, String> imageInfo = new HashMap<>();
                imageInfo.put("imageUrl", existedImageUris.get(i).toString());
                imageInfo.put("imageName", existedImageNames.get(i));
                imageInfo.put("imagePosition", String.valueOf(existedImagePositions.get(i)));
                imageData.add(imageInfo);
            }
        }

        int totalUpdatedImages = updatedImageUris.size();

        if (totalUpdatedImages != 0) {
            final int[] completedImages = {0};
            for (int i = 0; i < totalUpdatedImages; i++) {
                Uri updatedImageUri = updatedImageUris.get(i);
                String updatedImageName = updatedImageNames.get(i);
                int imagePosition = updatedImagePositions.get(i);
                String updatedImageExtension = getFileExtension(updatedImageUri);

                String uniqueImageName = UUID.randomUUID().toString() + "_" + updatedImageName;
                StorageReference fileRef = storage.getReference().child("images/" + uniqueImageName);

                fileRef.putFile(updatedImageUri)
                        .addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            Map<String, String> imageInfo = new HashMap<>();
                            imageInfo.put("imageName", updatedImageName);
                            imageInfo.put("imageExtension", updatedImageExtension != null ? updatedImageExtension : "unknown");
                            imageInfo.put("imageUrl", uri.toString());
                            imageInfo.put("imagePosition", String.valueOf(imagePosition));
                            imageData.add(imageInfo);

                            completedImages[0]++;
                            if (completedImages[0] == totalUpdatedImages) {
                                completedTasks.incrementAndGet();
                                checkAndSavePost(category, title);
                            }
                        }))
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "이미지 업로드 실패: " + updatedImageName, Toast.LENGTH_SHORT).show();
                        });
            }
        } else {
            completedTasks.incrementAndGet();
        }
    }

    // 스토리지에서 파일 삭제 후 후속 작업 실행
    private void deleteFilesFromStorage(String category, String title) {
        final List<StorageReference> fileReferences = new ArrayList<>();
        for (Uri fileUri : deletedFileUris) {
            fileReferences.add(storage.getReferenceFromUrl(fileUri.toString()));
        }

        final int totalFiles = fileReferences.size();
        final int[] completedFiles = {0};

        for (StorageReference fileRef : fileReferences) {
            fileRef.delete()
                    .addOnSuccessListener(aVoid -> {
                        completedFiles[0]++;
                            if (completedFiles[0] == totalFiles) {
                                completedTasks.incrementAndGet();
                                checkAndSavePost(category, title);
                            }
                            if (completedFiles[0] == totalFiles) {
                                deletedTasks.incrementAndGet();
                                checkAndDeletePost();
                            }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "파일 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    // 스토리지에서 파일 삭제 후 후속 작업 실행
    private void deleteFilesFromStorageForDeletePost() {
        final List<StorageReference> fileReferences = new ArrayList<>();
        for (Uri fileUri : deletedFileUris) {
            fileReferences.add(storage.getReferenceFromUrl(fileUri.toString()));
        }

        final int totalFiles = fileReferences.size();
        final int[] completedFiles = {0};

        for (StorageReference fileRef : fileReferences) {
            fileRef.delete()
                    .addOnSuccessListener(aVoid -> {
                        completedFiles[0]++;
                        if (completedFiles[0] == totalFiles) {
                            deletedTasks.incrementAndGet();
                            checkAndDeletePost();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "파일 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    // 수정된 게시글 내용을 데이터베이스에 저장
    private void saveUpdatedPostToDB(String category, String title,
                                     List<Map<String, String>> fileData, List<Map<String, String>> imageData) {
        String htmlContent = convertToHtmlStyledContent();
        db.collection("posts").document(documentId)
                .update("category", category, "title", title, "content", htmlContent, "files", fileData, "images", imageData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "글이 수정되었습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "글 수정에 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
    }

    // 새 파일을 업로드하고 게시글 업데이트
    private void uploadNewFilesToStorage(String category, String title) {
        if (!existedFileUris.isEmpty()) {
            for (int i = 0; i < existedFileUris.size(); i++) {
                Map<String, String> fileInfo = new HashMap<>();
                fileInfo.put("fileName", existedFileNames.get(i));
                fileInfo.put("fileExtension", existedFileExtensions.get(i));
                fileInfo.put("fileUrl", existedFileUris.get(i).toString());
                fileData.add(fileInfo);
            }
        }

        int totalUpdatedFiles = updatedFileUris.size();

        if (totalUpdatedFiles != 0) {
            final int[] completedFiles = {0};
            for (int i = 0; i < totalUpdatedFiles; i++) {
                Uri updatedFileUri = updatedFileUris.get(i);
                if (!isLocalUri(updatedFileUri)) {
                    Toast.makeText(this, "유효하지 않은 파일 URI: " + updatedFileUri, Toast.LENGTH_SHORT).show();
                    continue;
                }

                String updatedFileName = updatedFileNames.get(i);
                String updatedFileExtension = getFileExtension(updatedFileUri);
                String uniqueFileName = UUID.randomUUID().toString() + "_" + updatedFileName;
                StorageReference fileRef = storage.getReference().child("files/" + uniqueFileName);

                fileRef.putFile(updatedFileUri)
                        .addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            Map<String, String> fileInfo = new HashMap<>();
                            fileInfo.put("fileName", updatedFileName);
                            fileInfo.put("fileExtension", updatedFileExtension != null ? updatedFileExtension : "unknown");
                            fileInfo.put("fileUrl", uri.toString());
                            fileData.add(fileInfo);

                            completedFiles[0]++;
                            if (completedFiles[0] == totalUpdatedFiles) {
                                completedTasks.incrementAndGet();
                                checkAndSavePost(category, title);
                            }
                        }))
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "파일 업로드 실패: " + updatedFileName, Toast.LENGTH_SHORT).show();
                        });
            }
        } else {
            completedTasks.incrementAndGet();
        }
    }

    private void checkAndSavePost(String category, String title) {
        if (completedTasks.get() == totalTasks) {
            saveUpdatedPostToDB(category, title, fileData, imageData);
        }
    }

    // 로컬 URI인지 확인 (즉, 휴대폰에 존재하는 파일인지 확인)
    private boolean isLocalUri(Uri uri) {
        String scheme = uri.getScheme();
        return scheme != null && (scheme.equals("content") || scheme.equals("file"));
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

    // 게시글 삭제를 확인 후 실행
    private void deletePost() {
        new AlertDialog.Builder(this)
                .setTitle("삭제 확인")
                .setMessage("정말 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, which) -> {
                    if (!existedFileUris.isEmpty()) {
                        deletedFileUris.addAll(existedFileUris);
                        deleteFilesFromStorageForDeletePost();
                    } else {
                        deletedTasks.incrementAndGet();
                    }

                    if (!existedImageUris.isEmpty()) {
                        deletedImageUris.addAll(existedImageUris);
                        deleteImagesFromStorageForDeletePost();
                    } else {
                        deletedTasks.incrementAndGet();
                    }

                    checkAndDeletePost();
                })
                .setNegativeButton("취소", (dialog, which) -> {})
                .show();
    }

    private void checkAndDeletePost() {
        if (deletedTasks.get() == totaldeleteTasks) {
            deletePostFromDB();
        }
    }

    // 데이터베이스에서 게시글 삭제
    private void deletePostFromDB() {
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

    // 서식 버튼 리스너 설정
    private void setupButtonListeners() {
        boldButton.setOnClickListener(v -> {
            isBold = !isBold;
            toggleStyle(contentEditText, new StyleSpan(Typeface.BOLD), isBold);
        });

        italicButton.setOnClickListener(v -> {
            isItalic = !isItalic;
            toggleStyle(contentEditText, new StyleSpan(Typeface.ITALIC), isItalic);
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

    // 텍스트에 스타일 적용
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

    // 텍스트 변경 감지 및 스타일 적용
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

    // 텍스트 범위에 현재 스타일 적용
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
        int imageIndex = 0;   // 이미지 데이터의 현재 인덱스

        ImageSpan[] imageSpans = text.getSpans(0, text.length(), ImageSpan.class);

        while (currentIndex < text.length()) {
            boolean imageProcessed = false;

            for (ImageSpan imageSpan : imageSpans) {
                int start = text.getSpanStart(imageSpan);
                int end = text.getSpanEnd(imageSpan);

                if (currentIndex == start) {
                    String imageUrl = imageData.get(imageIndex).get("imageUrl");
                    if (imageUrl != null) {
                        htmlContent.append("<img src=\"").append(imageUrl).append("\" />");
                    }
                    currentIndex = end;
                    imageIndex++;
                    imageProcessed = true;
                    break;
                }
            }

            if (!imageProcessed) {
                char ch = text.charAt(currentIndex++);
                if (ch == '\n') {
                    htmlContent.append("<br>");
                } else {
                    htmlContent.append(processStyledCharacter(text, currentIndex - 1, ch));
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
