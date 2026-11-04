"""Import in Xcode LLDB. arm64 only; captures the first channel error's emitting stack.
An asynchronous stack may not include the originating UIKit prepare/impact call.
"""
import lldb

_hit = False


def on_log(frame, bp_loc, internal_dict):
    global _hit
    if _hit:
        return False
    process = frame.GetThread().GetProcess()
    error = lldb.SBError()
    address = frame.FindRegister("x3").GetValueAsUnsigned()
    if not address:
        return False
    message = process.ReadCStringFromMemory(address, 4096, error)
    if error.Fail() or "Unable to add an additional player channel" not in message:
        return False
    _hit = True
    print("[HapticChannelFailure] FIRST channel error; trial interrupted by debugger")
    print(message)
    for current in frame.GetThread():
        print(current)
    print("Save console and run 'thread backtrace all'. Restart app before next trial.")
    return True


def __lldb_init_module(debugger, internal_dict):
    target = debugger.GetSelectedTarget()
    if "arm64" not in (target.GetTriple() or ""):
        print("Not installed: requires arm64 iPhone target")
        return
    for name in ("_os_log_error_impl", "_os_log_fault_impl"):
        bp = target.BreakpointCreateByName(name)
        bp.SetScriptCallbackFunction(__name__ + ".on_log")
        print("[HapticChannelFailure] breakpoint", bp.GetID(), name, "locations=", bp.GetNumLocations())
    print("First matching error stops. Other logs auto-continue. Delete these breakpoints for timing-only trials.")
