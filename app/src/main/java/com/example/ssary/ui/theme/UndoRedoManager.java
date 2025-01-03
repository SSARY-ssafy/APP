package com.example.ssary.ui.theme;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Stack;

public class UndoRedoManager {
    public static class State {
        public CharSequence text;
        public List<Uri> imageUris;
        public List<String> imageNames;
        public List<Integer> imagePositions;

        public State(CharSequence text, List<Uri> imageUris, List<String> imageNames, List<Integer> imagePositions) {
            this.text = text;
            this.imageUris = new ArrayList<>(imageUris);
            this.imageNames = new ArrayList<>(imageNames);
            this.imagePositions = new ArrayList<>(imagePositions);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;

            State state = (State) obj;
            return text.equals(state.text) &&
                    imageUris.equals(state.imageUris) &&
                    imageNames.equals(state.imageNames) &&
                    imagePositions.equals(state.imagePositions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(text, imageUris, imageNames, imagePositions);
        }
    }

    private final Stack<State> undoStack = new Stack<>();
    private final Stack<State> redoStack = new Stack<>();

    public void saveState(State newState) {
        if (!undoStack.isEmpty() && undoStack.peek().equals(newState)) {
            return; // 동일한 상태는 저장하지 않음
        }
        undoStack.push(newState);
        redoStack.clear(); // 새로운 상태가 저장되면 redo 스택 초기화
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public State undo() {
        if (!canUndo()) throw new IllegalStateException("Undo stack is empty");

        State currentState = undoStack.pop();
        redoStack.push(currentState);

        return undoStack.peek(); // 이전 상태 반환
    }

    public State redo() {
        if (!canRedo()) throw new IllegalStateException("Redo stack is empty");

        State nextState = redoStack.pop();
        undoStack.push(nextState);

        return nextState; // 다음 상태 반환
    }

}


