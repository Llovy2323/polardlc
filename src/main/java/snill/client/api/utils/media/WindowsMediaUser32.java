package snill.client.api.utils.media;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;

public interface WindowsMediaUser32 extends StdCallLibrary {
    WindowsMediaUser32 INSTANCE = Native.load("user32", WindowsMediaUser32.class);

    interface WNDENUMPROC extends StdCallCallback {
        boolean callback(Pointer hWnd, Pointer arg);
    }

    boolean EnumWindows(WNDENUMPROC lpEnumFunc, Pointer arg);
    int GetWindowTextW(Pointer hWnd, char[] lpString, int nMaxCount);
    boolean IsWindowVisible(Pointer hWnd);
    int GetWindowTextLengthW(Pointer hWnd);
    int GetWindowThreadProcessId(Pointer hWnd, int[] lpdwProcessId);
    void keybd_event(byte bVk, byte bScan, int dwFlags, int dwExtraInfo);
}
