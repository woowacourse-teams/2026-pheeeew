"""Exact error-branch breakpoint for CoreHaptics UUID from iOS 26.6 (23G71).
Offset +208 verified against the local device's disassembly: the log call for
'Unable to add an additional player channel'. Other framework versions refuse.
"""
import lldb


def __lldb_init_module(debugger, internal_dict):
    target = debugger.GetSelectedTarget()
    name = '-[CHHapticEngine(CHHapticEngineInternal) getAvailableChannel:]'
    matches = target.FindFunctions(name)
    if not matches.GetSize():
        debugger.HandleCommand('target modules add "/Users/USER/Library/Developer/Xcode/iOS DeviceSupport/iPhone18,1 26.6 (23G71)/Symbols/System/Library/Frameworks/CoreHaptics.framework/CoreHaptics"')
        matches = target.FindFunctions(name)
    for context in matches:
        module = context.GetModule()
        if module.GetUUIDString() != '8796BB8E-BECE-32AA-A5D4-ACB646121F9D':
            continue
        symbol = context.GetSymbol()
        start = symbol.GetStartAddress() if symbol.IsValid() else context.GetFunction().GetStartAddress()
        address = module.ResolveFileAddress(start.GetFileAddress() + 208)
        bp = target.BreakpointCreateBySBAddress(address)
        bp.SetOneShot(True)
        print('[HapticChannelFailure] Exact error branch breakpoint', bp.GetID(), hex(address.GetFileAddress()))
        print('Only the error branch stops; ordinary log calls are not intercepted.')
        return
    raise RuntimeError('Matching CoreHaptics UUID/function not found; no guessed address installed')
