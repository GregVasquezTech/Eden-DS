// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

// SPDX-FileCopyrightText: Copyright 2018 yuzu Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

#include <algorithm>
#include <cinttypes>
#include <cstring>
#include <limits>
#include <string_view>
#include <vector>

#include "common/common_funcs.h"
#include "common/hex_util.h"
#include "common/logging.h"
#include "common/lz4_compression.h"
#include "common/settings.h"
#include "common/swap.h"
#include "core/core.h"
#include "core/file_sys/patch_manager.h"
#include "core/hle/kernel/code_set.h"
#include "core/hle/kernel/k_page_table.h"
#include "core/hle/kernel/k_process.h"
#include "core/hle/kernel/k_thread.h"
#include "core/loader/nso.h"
#include "core/memory.h"

#ifdef HAS_NCE
#include "core/arm/nce/patcher.h"
#endif

namespace Loader {
namespace {
struct MODHeader {
    u32_le magic;
    u32_le dynamic_offset;
    u32_le bss_start_offset;
    u32_le bss_end_offset;
    u32_le eh_frame_hdr_start_offset;
    u32_le eh_frame_hdr_end_offset;
    u32_le module_offset; // Offset to runtime-generated module object. typically equal to .bss base
};
static_assert(sizeof(MODHeader) == 0x1c, "MODHeader has incorrect size.");

constexpr u32 PageAlignSize(u32 size) {
    return static_cast<u32>((size + Core::Memory::YUZU_PAGEMASK) & ~Core::Memory::YUZU_PAGEMASK);
}

#if defined(BOTW_DUALSCREEN_COMPANION)




constexpr u64 BotwCompanionTitleId = 0x01007EF00011E000ULL;

constexpr bool EnableBotwCompanion = true;
constexpr std::array<u8, 0x20> BotwCompanionBuildId{
    0xCD, 0x57, 0xB2, 0x3F, 0xA4, 0xBB, 0xAD, 0x65,
    0x80, 0x3D, 0x97, 0x88, 0xC0, 0x18, 0x21, 0xEE,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
};

constexpr u32 BotwTextLocation = 0x00000000;
constexpr u32 BotwTextFileSize = 0x015A1C64;
constexpr u32 BotwRoDataLocation = 0x015A2000;
constexpr u32 BotwRoDataSize = 0x005D7330;
constexpr u32 BotwDataLocation = 0x01B7A000;
constexpr u32 BotwDataSize = 0x002381D8;
constexpr u32 BotwBssSize = 0x000E5E28;

constexpr u32 BotwEquipHookOffset = 0x00B24570;
constexpr u32 BotwEquipHookInstruction = 0x39422008;



constexpr u32 BotwEquipCaveOffset = 0x015A1C64;
constexpr u32 BotwEquipCaveSize = BotwRoDataLocation - BotwEquipCaveOffset;
constexpr u32 BotwPouchSingletonGotOffset = 0x01D67AF0;
constexpr u32 BotwContextVtableGotOffset = 0x01D66260;




constexpr u32 BotwActionContextNameOffset = 0x015CCFC9;
constexpr std::string_view BotwActionContextName = "MainShortCut_00Screen";
constexpr u32 BotwPouchLockOffset = 0x00EAFF08;
constexpr u32 BotwPouchUnlockOffset = 0x00EAFF18;
constexpr u32 BotwQuickEquipOffset = 0x00D9A4E0;
constexpr u32 BotwAutoEquipOffset = 0x00D93168;



constexpr u32 BotwRequestEquippedItemOffset = 0x00E88FB0;
constexpr u32 BotwEquipmentActorsReadyOffset = 0x00B249D4;
constexpr u32 BotwPlayerInfoStorageOffset = 0x01E13ED0;
constexpr u32 BotwGetPlayerOffset = 0x00CC6FD0;
constexpr u32 BotwSwitchEquipmentCalcOffset = 0x00766E68;
constexpr u32 BotwSwitchEquipmentApplyOffset = 0x00766F14;



constexpr u32 BotwRequestArmorOffset = 0x00B252C4;



constexpr u32 BotwRuneManagerStaticOffset = 0x01E0CD40;
constexpr u32 BotwRuneSetCurrentOffset = 0x00B33D6C;



constexpr u32 BotwFastTravelEventOffset = 0x00E81A30;
constexpr u32 BotwCompanionMailboxPageSize = 0x1000;






constexpr u64 Mk8dCompanionTitleId = 0x0100152000022000ULL;
constexpr std::array<u8, 0x20> Mk8dCompanionBuildId{
    0xFE, 0x94, 0x1E, 0xD5, 0xBA, 0x14, 0xBE, 0x5D,
    0x50, 0x56, 0x98, 0xDA, 0x1B, 0xBF, 0x4F, 0xE7,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
};
constexpr u32 Mk8dTextLocation = 0x00000000;
constexpr u32 Mk8dTextFileSize = 0x00B510C8;
constexpr u32 Mk8dRoDataLocation = 0x00B52000;
constexpr u32 Mk8dRoDataSize = 0x00654C68;
constexpr u32 Mk8dDataLocation = 0x011A7000;
constexpr u32 Mk8dDataSize = 0x0016ABE0;
constexpr u32 Mk8dBssSize = 0x00051420;
constexpr u32 Mk8dItemInputHookOffset = 0x0003F42C;
constexpr u32 Mk8dItemInputHookInstruction = 0xF9402E88;


constexpr u32 Mk8dItemBridgeOffset = Mk8dTextFileSize;
constexpr u32 Mk8dItemBridgeSize = Mk8dRoDataLocation - Mk8dItemBridgeOffset;
constexpr u32 Mk8dCompanionMailboxPageSize = 0x1000;

constexpr u32 EncodeCompareBranchZero(bool is_64_bit, u32 target_register,
                                      size_t instruction_index, size_t target_index) {
    const s64 delta = static_cast<s64>(target_index) - static_cast<s64>(instruction_index);
    return (is_64_bit ? 0xB4000000U : 0x34000000U) |
           ((static_cast<u32>(delta) & 0x7FFFFU) << 5) | (target_register & 0x1FU);
}

constexpr u32 EncodeConditionalBranch(u32 condition, size_t instruction_index,
                                      size_t target_index) {
    const s64 delta = static_cast<s64>(target_index) - static_cast<s64>(instruction_index);
    return 0x54000000U | ((static_cast<u32>(delta) & 0x7FFFFU) << 5) |
           (condition & 0xFU);
}

constexpr u32 EncodeLocalBranch(size_t instruction_index, size_t target_index) {
    const s64 delta = static_cast<s64>(target_index) - static_cast<s64>(instruction_index);
    return 0x14000000U | (static_cast<u32>(delta) & 0x03FFFFFFU);
}

constexpr u32 EncodeRelativeBranch(u32 instruction_offset, u32 target_offset) {
    const s64 delta = static_cast<s64>(target_offset) - static_cast<s64>(instruction_offset);
    return 0x14000000U | (static_cast<u32>(delta / 4) & 0x03FFFFFFU);
}

constexpr u32 EncodeRelativeCall(u32 instruction_offset, u32 target_offset) {
    const s64 delta = static_cast<s64>(target_offset) - static_cast<s64>(instruction_offset);
    return 0x94000000U | (static_cast<u32>(delta / 4) & 0x03FFFFFFU);
}

constexpr u32 EncodeAdrp(u32 target_register, u32 instruction_offset, u32 target_offset) {
    const s64 instruction_page = static_cast<s64>(instruction_offset & ~0xFFFU);
    const s64 target_page = static_cast<s64>(target_offset & ~0xFFFU);
    const u64 pages =
        static_cast<u64>((target_page - instruction_page) / 0x1000) & 0x1FFFFFU;
    return 0x90000000U | (static_cast<u32>(pages & 3U) << 29) |
           (static_cast<u32>((pages >> 2) & 0x7FFFFU) << 5) | (target_register & 0x1FU);
}

constexpr u32 EncodeAddPageOffset(u32 target_register, u32 target_offset) {
    return 0x91000000U | ((target_offset & 0xFFFU) << 10) |
           ((target_register & 0x1FU) << 5) | (target_register & 0x1FU);
}

bool IsExactBotwCompanionMain(const NSOHeader& header, std::string_view name) {
    return name == "main" &&
           header.build_id == BotwCompanionBuildId &&
           header.segments[0].location == BotwTextLocation &&
           header.segments[0].size == BotwTextFileSize &&
           header.segments[1].location == BotwRoDataLocation &&
           header.segments[1].size == BotwRoDataSize &&
           header.segments[2].location == BotwDataLocation &&
           header.segments[2].size == BotwDataSize &&
           header.segments[2].bss_size == BotwBssSize;
}

bool IsExactMk8dCompanionMain(const NSOHeader& header, std::string_view name) {
    return name == "main" &&
           header.build_id == Mk8dCompanionBuildId &&
           header.segments[0].location == Mk8dTextLocation &&
           header.segments[0].size == Mk8dTextFileSize &&
           header.segments[1].location == Mk8dRoDataLocation &&
           header.segments[1].size == Mk8dRoDataSize &&
           header.segments[2].location == Mk8dDataLocation &&
           header.segments[2].size == Mk8dDataSize &&
           header.segments[2].bss_size == Mk8dBssSize;
}

u32 ReadCodeWord(const std::vector<u8>& image, size_t offset) {
    u32 word{};
    if (offset + sizeof(word) <= image.size()) {
        std::memcpy(&word, image.data() + offset, sizeof(word));
    }
    return word;
}




std::optional<u32> ReserveCompanionMailbox(const NSOHeader& header, std::string_view name,
                                           Kernel::CodeSet& codeset, u32& image_size) {
    const bool botw = EnableBotwCompanion && IsExactBotwCompanionMain(header, name);
    const bool mk8d = IsExactMk8dCompanionMain(header, name);
    const u32 page_size = mk8d ? Mk8dCompanionMailboxPageSize : BotwCompanionMailboxPageSize;
    if ((!botw && !mk8d) || image_size > std::numeric_limits<u32>::max() - page_size) {
        return std::nullopt;
    }
    const u32 mailbox_offset = image_size;
    codeset.DataSegment().size += page_size;
    image_size += page_size;
    codeset.memory.resize(image_size);
    return mailbox_offset;
}

bool InstallBotwCompanionEquipHook(std::vector<u8>& image, size_t module_start,
                                   u32 mailbox_main_offset) {
    std::vector<u32> code;
    code.reserve(BotwEquipCaveSize / sizeof(u32));
    const auto emit = [&](u32 instruction) {
        const size_t index = code.size();
        code.push_back(instruction);
        return index;
    };
    const auto emit_address = [&](u32 target_register, u32 target_offset) {
        const auto instruction_index = code.size();
        const auto instruction_offset =
            BotwEquipCaveOffset + static_cast<u32>(instruction_index * sizeof(u32));
        emit(EncodeAdrp(target_register, instruction_offset, target_offset));
        emit(EncodeAddPageOffset(target_register, target_offset));
    };


    const auto emit_call = [&](u32 target_offset) {
        const auto instruction_offset =
            BotwEquipCaveOffset + static_cast<u32>(code.size() * sizeof(u32));
        const s64 delta =
            static_cast<s64>(target_offset) - static_cast<s64>(instruction_offset);
        ASSERT_MSG((delta & 3) == 0 && delta >= -(1LL << 27) && delta < (1LL << 27),
                   "BOTW companion direct-call target is outside AArch64 BL range");
        emit(EncodeRelativeCall(instruction_offset, target_offset));
    };




    emit_address(9, mailbox_main_offset);
    emit(0x9100412AU);
    emit(0xB940014BU);
    emit(0x7100097FU);
    const auto branch_running = emit(0);
    emit(0x7100057FU);
    const auto branch_not_queued = emit(0);
    emit(0xD5033BBFU);
    emit(0xF940012BU);
    const auto branch_no_target = emit(0);
    emit(0xD10143FFU);
    emit(0xA9037BE0U);
    emit(0xF90023EBU);
    emit(0x5280004CU);
    emit(0xB900014CU);
    emit(0xB940192CU);
    emit(0x3100059FU);
    const auto branch_rune = emit(0);
    emit(0x3100099FU);
    const auto branch_fast_travel = emit(0);
    emit_address(12, BotwPouchSingletonGotOffset);
    emit(0xF940018CU);
    const auto branch_null_got = emit(0);
    emit(0xF940018CU);
    const auto branch_null_manager = emit(0);
    emit(0xF90013ECU);
    emit(0x9100A180U);
    emit_call(BotwPouchLockOffset);
    emit(0xF94013ECU);
    emit(0xF94023EBU);
    emit(0x9101A18EU);
    emit(0xB940798FU);
    emit(0x710005FFU);
    const auto branch_bad_count_low = emit(0);
    emit(0x710691FFU);
    const auto branch_bad_count_high = emit(0);
    emit(0xF940398DU);

    const auto loop = code.size();
    emit(0xEB0E01BFU);
    const auto branch_end_of_list = emit(0);
    emit(0xD10021B0U);
    emit(0xEB0B021FU);
    const auto branch_found = emit(0);
    emit(0xF9400A0DU);
    emit(0x710005EFU);
    const auto branch_continue_loop = emit(0);
    const auto branch_exhausted = emit(0);

    const auto found = code.size();
    emit(0x3940956DU);
    const auto branch_not_usable = emit(0);
    emit(0xB940196DU);



    emit(0x710009BFU);
    const auto branch_bad_type_2 = emit(0);
    emit(0x71000DBFU);
    const auto branch_weapon_type = emit(0);
    emit(0x710019BFU);
    const auto branch_bad_type_high = emit(0);

    emit(0xAA0C03E0U);
    emit(0xAA0B03E1U);
    emit(0xAA0E03E2U);
    emit_call(BotwAutoEquipOffset);
    emit(0xF94013ECU);
    emit(0x9100A180U);
    emit_call(BotwPouchUnlockOffset);
    emit(0xF9401BE0U);
    emit(0xF94023EBU);
    emit(0x9100A161U);
    emit(0xB9402162U);
    emit_address(8, BotwContextVtableGotOffset);
    emit(0xF9400108U);
    emit(0x91004108U);
    emit_address(9, BotwActionContextNameOffset);
    emit(0xA90027E8U);
    emit(0x910003E3U);
    emit_call(BotwRequestArmorOffset);
    const auto branch_armor_staged = emit(0);

    const auto weapon = code.size();
    emit(0xF94013ECU);
    emit(0x9100A180U);
    emit_call(BotwPouchUnlockOffset);
    emit(0xF94013E0U);
    emit(0xF94023E1U);
    emit_call(BotwQuickEquipOffset);
    emit(0xF94023E0U);
    emit_address(8, BotwContextVtableGotOffset);
    emit(0xF9400108U);
    emit(0x91004108U);
    emit_address(9, BotwActionContextNameOffset);
    emit(0xA90027E8U);
    emit(0x910003E1U);
    emit_call(BotwRequestEquippedItemOffset);
    const auto branch_weapon_reject = emit(0);
    const auto branch_weapon_staged = emit(0);




    const auto fast_travel = code.size();
    emit(0x91010120U);
    emit(0x91020121U);
    emit(0xAA1F03E2U);
    emit_call(BotwFastTravelEventOffset);
    emit(0xA9437BE0U);
    emit(0x910143FFU);


    const auto branch_fast_travel_staged = emit(0);




    const auto rune = code.size();
    emit(0x5100056BU);
    emit(0x7100157FU);
    const auto branch_rune_bad = emit(0);
    emit_address(12, BotwRuneManagerStaticOffset);
    emit(0xF9400180U);
    const auto branch_rune_null = emit(0);
    emit(0x2A0B03E1U);
    emit_call(BotwRuneSetCurrentOffset);
    emit(0xA9437BE0U);
    emit(0x910143FFU);
    emit_address(9, mailbox_main_offset);
    const auto branch_rune_success = emit(0);





    const auto running = code.size();
    emit(0xD5033BBFU);
    emit(0xF940012BU);
    const auto branch_running_no_target = emit(0);
    emit(0xB9401D2AU);
    emit(0x1100054AU);
    emit(0xB9001D2AU);
    emit(0x711C215FU);
    const auto branch_running_timeout = emit(0);
    emit(0xB940192CU);
    emit(0x3100099FU);
    const auto branch_running_fast_travel = emit(0);
    emit(0x7100019FU);
    const auto branch_running_type_0 = emit(0);
    emit(0x7100059FU);
    const auto branch_running_type_1 = emit(0);
    emit(0x71000D9FU);
    const auto branch_running_type_3 = emit(0);
    emit(0x7100119FU);
    const auto branch_running_type_4 = emit(0);
    emit(0x7100159FU);
    const auto branch_running_type_5 = emit(0);
    emit(0x7100199FU);
    const auto branch_running_bad_type = emit(0);
    emit(0x528000ADU);
    const auto branch_running_slot_5 = emit(0);

    const auto running_type_0 = code.size();
    emit(0x5280000DU);
    const auto branch_running_slot_0 = emit(0);
    const auto running_type_1 = code.size();
    emit(0x5280004DU);
    const auto branch_running_slot_2 = emit(0);
    const auto running_type_3 = code.size();
    emit(0x5280002DU);
    const auto branch_running_slot_1 = emit(0);
    const auto running_type_4 = code.size();
    emit(0x5280006DU);
    const auto branch_running_slot_3 = emit(0);
    const auto running_type_5 = code.size();
    emit(0x5280008DU);

    const auto running_status = code.size();
    emit(0x52800D0EU);
    emit(0x9BAE01AEU);
    emit(0x9103A1CEU);
    emit(0xB94001C8U);
    const auto branch_running_idle = emit(0);
    emit(0x7100051FU);
    const auto branch_running_loading = emit(0);
    emit(0x7100091FU);
    const auto branch_running_bad_status = emit(0);
    emit(0xD100C3FFU);
    emit(0xA9017BE0U);
    emit_call(BotwEquipmentActorsReadyOffset);
    const auto branch_running_wait = emit(0);
    emit_address(8, BotwPlayerInfoStorageOffset);
    emit(0xF9400100U);
    const auto branch_running_null_storage = emit(0);
    emit_call(BotwGetPlayerOffset);
    const auto branch_running_null_player = emit(0);
    emit(0xF90003E0U);
    emit(0x91316000U);
    emit_call(BotwPouchLockOffset);
    emit(0xF94003EBU);
    emit(0xB94C9968U);
    emit(0x321B0108U);
    emit(0xB90C9968U);
    emit(0x91316160U);
    emit_call(BotwPouchUnlockOffset);

    const auto success = code.size();
    emit(0xA9417BE0U);
    emit(0x9100C3FFU);
    emit_address(9, mailbox_main_offset);
    const auto success_publish = code.size();
    emit(0xF940012BU);
    emit(0xF900052BU);
    emit(0xF900013FU);
    emit(0xF9000D3FU);
    emit(0x5280006AU);
    emit(0xD5033BBFU);
    emit(0xB900112AU);
    const auto branch_success_passthrough = emit(0);

    const auto running_wait = code.size();
    emit(0xA9417BE0U);
    emit(0x9100C3FFU);
    const auto branch_wait_passthrough = emit(0);



    const auto running_fast_travel = code.size();
    emit(0x7104B15FU);
    const auto branch_fast_travel_wait = emit(0);
    const auto branch_fast_travel_complete = emit(0);

    const auto running_reject = code.size();
    emit(0xA9417BE0U);
    emit(0x9100C3FFU);
    emit_address(9, mailbox_main_offset);
    const auto branch_running_reject = emit(0);



    const auto staged = code.size();
    emit(0xA9437BE0U);
    emit(0x910143FFU);
    const auto branch_staged_passthrough = emit(0);

    const auto unlock_reject = code.size();
    emit(0xF94013ECU);
    emit(0x9100A180U);
    emit_call(BotwPouchUnlockOffset);
    const auto branch_unlocked_reject = emit(0);

    const auto reject_stack = code.size();
    emit(0xF94023EBU);
    emit(0xA9437BE0U);
    emit(0x910143FFU);
    emit_address(9, mailbox_main_offset);
    const auto branch_reject_publish = emit(0);

    const auto reject_early = code.size();
    emit(0xF940012BU);
    const auto reject_publish = code.size();
    emit(0xF900052BU);
    emit(0xF900013FU);
    emit(0xF9000D3FU);
    emit(0x5280008AU);
    emit(0xD5033BBFU);
    emit(0xB900112AU);

    const auto passthrough = code.size();
    emit(BotwEquipHookInstruction);
    const auto return_index = code.size();
    emit(EncodeRelativeBranch(
        BotwEquipCaveOffset + static_cast<u32>(return_index * sizeof(u32)),
        BotwEquipHookOffset + sizeof(u32)));

    code[branch_running] =
        EncodeConditionalBranch(0, branch_running, running);
    code[branch_not_queued] =
        EncodeConditionalBranch(1, branch_not_queued, passthrough);
    code[branch_no_target] =
        EncodeCompareBranchZero(true, 11, branch_no_target, reject_early);
    code[branch_rune] = EncodeConditionalBranch(0, branch_rune, rune);
    code[branch_fast_travel] =
        EncodeConditionalBranch(0, branch_fast_travel, fast_travel);
    code[branch_fast_travel_staged] =
        EncodeLocalBranch(branch_fast_travel_staged, passthrough);
    code[branch_rune_bad] =
        EncodeConditionalBranch(8, branch_rune_bad, reject_stack);
    code[branch_rune_null] =
        EncodeCompareBranchZero(true, 0, branch_rune_null, reject_stack);
    code[branch_rune_success] =
        EncodeLocalBranch(branch_rune_success, success_publish);
    code[branch_null_got] =
        EncodeCompareBranchZero(true, 12, branch_null_got, reject_stack);
    code[branch_null_manager] =
        EncodeCompareBranchZero(true, 12, branch_null_manager, reject_stack);
    code[branch_bad_count_low] =
        EncodeConditionalBranch(0xB, branch_bad_count_low, unlock_reject);
    code[branch_bad_count_high] =
        EncodeConditionalBranch(0xC, branch_bad_count_high, unlock_reject);
    code[branch_end_of_list] =
        EncodeConditionalBranch(0, branch_end_of_list, unlock_reject);
    code[branch_found] = EncodeConditionalBranch(0, branch_found, found);
    code[branch_continue_loop] =
        EncodeConditionalBranch(1, branch_continue_loop, loop);
    code[branch_exhausted] = EncodeLocalBranch(branch_exhausted, unlock_reject);
    code[branch_not_usable] =
        EncodeCompareBranchZero(false, 13, branch_not_usable, unlock_reject);
    code[branch_bad_type_2] =
        EncodeConditionalBranch(0, branch_bad_type_2, unlock_reject);
    code[branch_weapon_type] =
        EncodeConditionalBranch(9, branch_weapon_type, weapon);
    code[branch_bad_type_high] =
        EncodeConditionalBranch(0xC, branch_bad_type_high, unlock_reject);
    code[branch_armor_staged] = EncodeLocalBranch(branch_armor_staged, staged);
    code[branch_weapon_reject] =
        EncodeCompareBranchZero(false, 0, branch_weapon_reject, reject_stack);
    code[branch_weapon_staged] = EncodeLocalBranch(branch_weapon_staged, staged);
    code[branch_running_no_target] =
        EncodeCompareBranchZero(true, 11, branch_running_no_target, reject_early);
    code[branch_running_timeout] =
        EncodeConditionalBranch(8, branch_running_timeout, reject_early);
    code[branch_running_fast_travel] =
        EncodeConditionalBranch(0, branch_running_fast_travel, running_fast_travel);
    code[branch_running_type_0] =
        EncodeConditionalBranch(0, branch_running_type_0, running_type_0);
    code[branch_running_type_1] =
        EncodeConditionalBranch(0, branch_running_type_1, running_type_1);
    code[branch_running_type_3] =
        EncodeConditionalBranch(0, branch_running_type_3, running_type_3);
    code[branch_running_type_4] =
        EncodeConditionalBranch(0, branch_running_type_4, running_type_4);
    code[branch_running_type_5] =
        EncodeConditionalBranch(0, branch_running_type_5, running_type_5);
    code[branch_running_bad_type] =
        EncodeConditionalBranch(1, branch_running_bad_type, reject_early);
    code[branch_running_slot_5] =
        EncodeLocalBranch(branch_running_slot_5, running_status);
    code[branch_running_slot_0] =
        EncodeLocalBranch(branch_running_slot_0, running_status);
    code[branch_running_slot_2] =
        EncodeLocalBranch(branch_running_slot_2, running_status);
    code[branch_running_slot_1] =
        EncodeLocalBranch(branch_running_slot_1, running_status);
    code[branch_running_slot_3] =
        EncodeLocalBranch(branch_running_slot_3, running_status);
    code[branch_running_idle] =
        EncodeCompareBranchZero(false, 8, branch_running_idle, reject_early);
    code[branch_running_loading] =
        EncodeConditionalBranch(0, branch_running_loading, passthrough);
    code[branch_running_bad_status] =
        EncodeConditionalBranch(1, branch_running_bad_status, reject_early);
    code[branch_running_wait] =
        EncodeCompareBranchZero(false, 0, branch_running_wait, running_wait);
    code[branch_running_null_storage] =
        EncodeCompareBranchZero(true, 0, branch_running_null_storage, running_reject);
    code[branch_running_null_player] =
        EncodeCompareBranchZero(true, 0, branch_running_null_player, running_reject);
    code[branch_unlocked_reject] = EncodeLocalBranch(branch_unlocked_reject, reject_stack);
    code[branch_success_passthrough] =
        EncodeLocalBranch(branch_success_passthrough, passthrough);
    code[branch_wait_passthrough] =
        EncodeLocalBranch(branch_wait_passthrough, passthrough);
    code[branch_fast_travel_wait] =
        EncodeConditionalBranch(3, branch_fast_travel_wait, passthrough);
    code[branch_fast_travel_complete] =
        EncodeLocalBranch(branch_fast_travel_complete, success_publish);
    code[branch_running_reject] =
        EncodeLocalBranch(branch_running_reject, reject_early);
    code[branch_staged_passthrough] =
        EncodeLocalBranch(branch_staged_passthrough, passthrough);
    code[branch_reject_publish] =
        EncodeLocalBranch(branch_reject_publish, reject_publish);

    static_assert((BotwEquipCaveOffset & 3U) == 0);
    static_assert(BotwEquipCaveOffset >= BotwTextFileSize);
    static_assert((BotwRuneManagerStaticOffset & 7U) == 0);
    static_assert(BotwRuneManagerStaticOffset >= BotwDataLocation);
    static_assert(BotwRuneManagerStaticOffset <
                  BotwDataLocation + BotwDataSize + BotwBssSize);
    const size_t code_size = code.size() * sizeof(u32);
    if (code_size > BotwEquipCaveSize) {
        LOG_ERROR(Loader,
                  "BOTW companion bridge generated {} bytes but its exact-build code cave holds "
                  "only {} bytes",
                  code_size, BotwEquipCaveSize);
        return false;
    }
    if (module_start > image.size() || BotwEquipHookOffset > image.size() - module_start ||
        BotwEquipCaveOffset > image.size() - module_start) {
        return false;
    }
    const size_t hook = module_start + BotwEquipHookOffset;
    const size_t cave = module_start + BotwEquipCaveOffset;
    const size_t context_name = module_start + BotwActionContextNameOffset;
    if (sizeof(u32) > image.size() - hook || code_size > image.size() - cave ||
        context_name > image.size() ||
        BotwActionContextName.size() + 1 > image.size() - context_name ||
        !std::equal(BotwActionContextName.begin(), BotwActionContextName.end(),
                    image.begin() + static_cast<ptrdiff_t>(context_name)) ||
        image[context_name + BotwActionContextName.size()] != 0 ||
        ReadCodeWord(image, hook) != BotwEquipHookInstruction ||
        ReadCodeWord(image, module_start + BotwQuickEquipOffset) != 0xB5000041U ||
        ReadCodeWord(image, module_start + BotwAutoEquipOffset) != 0xB9401828U ||
        ReadCodeWord(image, module_start + BotwRequestEquippedItemOffset) != 0xD10103FFU ||
        ReadCodeWord(image, module_start + BotwEquipmentActorsReadyOffset) != 0x39422008U ||
        ReadCodeWord(image, module_start + BotwGetPlayerOffset) != 0xF9403001U ||
        ReadCodeWord(image, module_start + BotwSwitchEquipmentCalcOffset) != 0xA9BD7BFDU ||
        ReadCodeWord(image, module_start + BotwSwitchEquipmentApplyOffset) != 0xB94C9A88U ||
        ReadCodeWord(image, module_start + BotwSwitchEquipmentApplyOffset + 8) != 0x321B0108U ||
        ReadCodeWord(image, module_start + BotwSwitchEquipmentApplyOffset + 12) != 0xB90C9A88U ||
        ReadCodeWord(image, module_start + BotwRequestArmorOffset) != 0xD10183FFU ||
        ReadCodeWord(image, module_start + BotwRuneSetCurrentOffset) != 0xA9BD7BFDU ||
        ReadCodeWord(image, module_start + BotwRuneSetCurrentOffset + 0x24) != 0xB9024293U ||
        ReadCodeWord(image, module_start + BotwFastTravelEventOffset) != 0xB40004A0U ||
        ReadCodeWord(image, module_start + BotwPouchLockOffset) != 0x91008000U ||
        ReadCodeWord(image, module_start + BotwPouchUnlockOffset) != 0x91008000U ||
        !std::all_of(image.begin() + static_cast<ptrdiff_t>(cave),
                     image.begin() + static_cast<ptrdiff_t>(cave + code_size),
                     [](u8 value) { return value == 0; })) {
        return false;
    }

    const u32 branch = EncodeRelativeBranch(BotwEquipHookOffset, BotwEquipCaveOffset);
    std::memcpy(image.data() + cave, code.data(), code_size);
    std::memcpy(image.data() + hook, &branch, sizeof(branch));
    return true;
}

bool InstallMk8dCompanionItemHook(std::vector<u8>& image, size_t module_start,
                                  u32 mailbox_main_offset) {
    std::vector<u32> code;
    code.reserve(96);
    const auto emit = [&](u32 instruction) {
        const size_t index = code.size();
        code.push_back(instruction);
        return index;
    };

    emit(EncodeAdrp(9, Mk8dItemBridgeOffset, mailbox_main_offset));
    emit(EncodeAddPageOffset(9, mailbox_main_offset));
    emit(0xB940112AU);
    emit(0x7100055FU);
    const auto branch_not_queued = emit(0);
    emit(0xF940268AU);
    const auto branch_no_proxy = emit(0);
    emit(0xF940254AU);
    const auto branch_no_vehicle = emit(0);
    emit(0x3943414BU);
    const auto branch_not_local = emit(0);
    emit(0xD5033BBFU);
    emit(0xF940012BU);
    const auto branch_empty_request = emit(0);
    emit(0xF100097FU);
    const auto branch_bad_slot = emit(0);
    emit(0x5280004AU);
    emit(0xB900112AU);
    emit(0x51000575U);
    emit(0x8B150E8AU);
    emit(0xF940314AU);
    const auto branch_null_slot = emit(0);
    emit(0xF9002E8AU);
    emit(0x1280000AU);
    emit(0xB900728AU);
    emit(0x52800038U);
    emit(0xF900052BU);
    emit(0xF900013FU);
    emit(0x5280006AU);
    emit(0xD5033BBFU);
    emit(0xB900112AU);
    const auto finish = code.size();
    emit(Mk8dItemInputHookInstruction);
    emit(0xD65F03C0U);
    const auto reject = code.size();
    emit(0xF900052BU);
    emit(0xF900013FU);
    emit(0x5280008AU);
    emit(0xD5033BBFU);
    emit(0xB900112AU);
    const auto branch_finish = emit(0);

    code[branch_not_queued] = EncodeConditionalBranch(1, branch_not_queued, finish);
    code[branch_no_proxy] = EncodeCompareBranchZero(true, 10, branch_no_proxy, finish);
    code[branch_no_vehicle] =
        EncodeCompareBranchZero(true, 10, branch_no_vehicle, finish);
    code[branch_not_local] =
        EncodeCompareBranchZero(false, 11, branch_not_local, finish);
    code[branch_empty_request] =
        EncodeCompareBranchZero(true, 11, branch_empty_request, reject);
    code[branch_bad_slot] = EncodeConditionalBranch(8, branch_bad_slot, reject);
    code[branch_null_slot] =
        EncodeCompareBranchZero(true, 10, branch_null_slot, reject);
    code[branch_finish] = EncodeLocalBranch(branch_finish, finish);

    const size_t code_size = code.size() * sizeof(u32);
    static_assert((Mk8dItemBridgeOffset & 3U) == 0);
    static_assert(Mk8dItemBridgeOffset >= Mk8dTextFileSize);
    if (code_size > Mk8dItemBridgeSize || module_start > image.size() ||
        Mk8dItemInputHookOffset > image.size() - module_start ||
        Mk8dItemBridgeOffset > image.size() - module_start) {
        return false;
    }
    const size_t item_hook = module_start + Mk8dItemInputHookOffset;
    const size_t cave = module_start + Mk8dItemBridgeOffset;
    if (sizeof(u32) > image.size() - item_hook ||
        code_size > image.size() - cave ||
        ReadCodeWord(image, item_hook) != Mk8dItemInputHookInstruction ||
        !std::all_of(image.begin() + static_cast<ptrdiff_t>(cave),
                     image.begin() + static_cast<ptrdiff_t>(cave + code_size),
                     [](u8 value) { return value == 0; })) {
        return false;
    }

    const u32 item_call = EncodeRelativeCall(Mk8dItemInputHookOffset, Mk8dItemBridgeOffset);
    std::memcpy(image.data() + cave, code.data(), code_size);
    std::memcpy(image.data() + item_hook, &item_call, sizeof(item_call));
    return true;
}

#endif

} // Anonymous namespace

bool NSOHeader::IsSegmentCompressed(size_t segment_num) const {
    ASSERT_MSG(segment_num < 3, "Invalid segment {}", segment_num);
    return ((flags >> segment_num) & 1) != 0;
}

AppLoader_NSO::AppLoader_NSO(FileSys::VirtualFile file_) : AppLoader(std::move(file_)) {}

FileType AppLoader_NSO::IdentifyType(const FileSys::VirtualFile& in_file) {
    u32 magic = 0;
    if (in_file->ReadObject(&magic) != sizeof(magic)) {
        return FileType::Error;
    }

    if (Common::MakeMagic('N', 'S', 'O', '0') != magic) {
        return FileType::Error;
    }

    return FileType::NSO;
}

std::optional<VAddr> AppLoader_NSO::LoadModule(Kernel::KProcess& process, Core::System& system, const FileSys::VfsFile& nso_file, VAddr load_base, bool should_pass_arguments, bool load_into_process, std::optional<FileSys::PatchManager> pm, std::vector<Core::NCE::Patcher>* patches, s32 patch_index) {
    if (nso_file.GetSize() < sizeof(NSOHeader))
        return std::nullopt;
    NSOHeader nso_header{};
    if (sizeof(NSOHeader) != nso_file.ReadObject(&nso_header))
        return std::nullopt;
    if (nso_header.magic != Common::MakeMagic('N', 'S', 'O', '0'))
        return std::nullopt;
    if (nso_header.segments.empty())
        return std::nullopt;

    // Allocate some space at the beginning if we are patching in PreText mode.
    const size_t module_start = [&]() -> size_t {
#ifdef HAS_NCE
        if (patches && load_into_process) {
            auto* patch = &patches->operator[](patch_index);
            if (patch->GetPatchMode() == Core::NCE::PatchMode::PreText) {
                return patch->GetSectionSize();
            } else if (patch->GetPatchMode() == Core::NCE::PatchMode::Split) {
                return patch->GetPreSectionSize();
            }
        }
#endif
        return 0;
    }();

    auto const last_segment_it = &nso_header.segments[nso_header.segments.size() - 1];
    // Build program image directly in codeset memory :)
    Kernel::CodeSet codeset;
    codeset.memory.resize(module_start + last_segment_it->location + last_segment_it->size);
    {
        std::vector<u8> compressed_data(*std::ranges::max_element(nso_header.segments_compressed_size));
        std::vector<u8> decompressed_size(std::ranges::max_element(nso_header.segments, [](auto const& a, auto const& b) {
            return a.size < b.size;
        })->size);
        for (std::size_t i = 0; i < nso_header.segments.size(); ++i) {
            nso_file.Read(compressed_data.data(), nso_header.segments_compressed_size[i], nso_header.segments[i].offset);
            if (nso_header.IsSegmentCompressed(i)) {
                int r = Common::Compression::DecompressDataLZ4(decompressed_size.data(), nso_header.segments[i].size, compressed_data.data(), nso_header.segments_compressed_size[i]);
                ASSERT(r == int(nso_header.segments[i].size));
                std::memcpy(codeset.memory.data() + module_start + nso_header.segments[i].location, decompressed_size.data(), nso_header.segments[i].size);
            } else {
                std::memcpy(codeset.memory.data() + module_start + nso_header.segments[i].location, compressed_data.data(), nso_header.segments[i].size);
            }
            codeset.segments[i].addr = module_start + nso_header.segments[i].location;
            codeset.segments[i].offset = module_start + nso_header.segments[i].location;
            codeset.segments[i].size = nso_header.segments[i].size;
        }
    }

    if (should_pass_arguments && !Settings::values.program_args.GetValue().empty()) {
        const auto arg_data{Settings::values.program_args.GetValue()};

        codeset.DataSegment().size += NSO_ARGUMENT_DATA_ALLOCATION_SIZE;
        NSOArgumentHeader args_header{NSO_ARGUMENT_DATA_ALLOCATION_SIZE, static_cast<u32_le>(arg_data.size()), {}};
        const auto end_offset = codeset.memory.size();
        codeset.memory.resize(u32(codeset.memory.size()) + NSO_ARGUMENT_DATA_ALLOCATION_SIZE);
        std::memcpy(codeset.memory.data() + end_offset, &args_header, sizeof(NSOArgumentHeader));
        std::memcpy(codeset.memory.data() + end_offset + sizeof(NSOArgumentHeader), arg_data.data(), arg_data.size());
    }

    codeset.DataSegment().size += nso_header.segments[2].bss_size;
    u32 image_size = PageAlignSize(u32(codeset.memory.size()) + nso_header.segments[2].bss_size);
    codeset.memory.resize(image_size);

    for (std::size_t i = 0; i < nso_header.segments.size(); ++i) {
        codeset.segments[i].size = PageAlignSize(codeset.segments[i].size);
    }

    // Apply patches if necessary
    const auto name = nso_file.GetName();
    if (pm && (pm->HasNSOPatch(nso_header.build_id, name) || Settings::values.dump_nso)) {
        std::span<u8> patchable_section(codeset.memory.data() + module_start, codeset.memory.size() - module_start);
        std::vector<u8> pi_header(sizeof(NSOHeader) + patchable_section.size());
        std::memcpy(pi_header.data(), &nso_header, sizeof(NSOHeader));
        std::memcpy(pi_header.data() + sizeof(NSOHeader), patchable_section.data(),
                    patchable_section.size());

        pi_header = pm->PatchNSO(pi_header, name);

        std::copy(pi_header.begin() + sizeof(NSOHeader), pi_header.end(), patchable_section.data());
    }

    u64 companion_mailbox_address{};
#if defined(BOTW_DUALSCREEN_COMPANION)
    const auto companion_mailbox_offset =
        ReserveCompanionMailbox(nso_header, name, codeset, image_size);
    bool companion_hook_installed{};
    if (companion_mailbox_offset) {


        if (module_start <= *companion_mailbox_offset &&
            *companion_mailbox_offset - module_start <= std::numeric_limits<u32>::max()) {
            const auto mailbox_main_offset =
                static_cast<u32>(*companion_mailbox_offset - module_start);
            if (IsExactMk8dCompanionMain(nso_header, name)) {
                companion_hook_installed = InstallMk8dCompanionItemHook(
                    codeset.memory, module_start, mailbox_main_offset);
            } else if (EnableBotwCompanion && IsExactBotwCompanionMain(nso_header, name)) {
                companion_hook_installed = InstallBotwCompanionEquipHook(
                    codeset.memory, module_start, mailbox_main_offset);
            }
        }
        if (load_into_process && companion_hook_installed && pm &&
            (pm->GetTitleID() == Mk8dCompanionTitleId ||
             (EnableBotwCompanion && pm->GetTitleID() == BotwCompanionTitleId))) {
            companion_mailbox_address = load_base + *companion_mailbox_offset;
        }
        if (load_into_process && !companion_hook_installed) {
            LOG_ERROR(Loader,
                      "Dual-screen companion bridge could not be installed in the load-pass "
                      "main NSO; refusing to run without its validated game-thread bridge");
            return std::nullopt;
        }
    }
#endif

#ifdef HAS_NCE
    // If we are computing the process code layout and using nce backend, patch.
    const auto& code = codeset.CodeSegment();
    auto* patch = patches ? &patches->operator[](patch_index) : nullptr;
    if (patch && !load_into_process) {
        //Set module ID using build_id from the NSO header
        patch->SetModuleID(nso_header.build_id);
        // Patch SVCs and MRS calls in the guest code
        while (!patch->PatchText(codeset.memory, code)) {
            patch = &patches->emplace_back();
            patch->SetModuleID(nso_header.build_id);  // In case the patcher is changed for big modules, the new patcher should also have the build_id
        }
    } else if (patch) {
        // Relocate code patch and copy to the program image.
        // Save size before RelocateAndCopy (which may resize)
        const size_t size_before_relocate = codeset.memory.size();
        if (patch->RelocateAndCopy(load_base, code, codeset.memory, &process.GetPostHandlers())) {
            // Update patch section.
            auto& patch_segment = codeset.PatchSegment();
            auto& post_patch_segment = codeset.PostPatchSegment();
            const auto patch_mode = patch->GetPatchMode();
            if (patch_mode == Core::NCE::PatchMode::PreText) {
                patch_segment.addr = 0;
                patch_segment.size = static_cast<u32>(patch->GetSectionSize());
            } else if (patch_mode == Core::NCE::PatchMode::Split) {
                // For Split-mode, we are using pre-patch buffer at start, post-patch buffer at end
                patch_segment.addr = 0;
                patch_segment.size = static_cast<u32>(patch->GetPreSectionSize());
                post_patch_segment.addr = size_before_relocate;
                post_patch_segment.size = static_cast<u32>(patch->GetSectionSize());
            } else {
                patch_segment.addr = image_size;
                patch_segment.size = static_cast<u32>(patch->GetSectionSize());
            }
        }

        // Refresh image_size to take account the patch section if it was added by RelocateAndCopy
        image_size = static_cast<u32>(codeset.memory.size());
    }
#endif

    // If we aren't actually loading (i.e. just computing the process code layout), we are done
    if (!load_into_process) {
#ifdef HAS_NCE
        // Ok, so for Split mode, we need to account for pre-patch and post-patch space
        // which will be added during RelocateAndCopy in the second pass. Where it crashed
        // in Android Studio at PreText. May be a better way. Works for now.
        if (patch && patch->GetPatchMode() == Core::NCE::PatchMode::Split) {
            return load_base + patch->GetPreSectionSize() + image_size + patch->GetSectionSize();
        } else if (patch && patch->GetPatchMode() == Core::NCE::PatchMode::PreText) {
            return load_base + patch->GetSectionSize() + image_size;
        } else if (patch && patch->GetPatchMode() == Core::NCE::PatchMode::PostData) {
            return load_base + image_size + patch->GetSectionSize();
        }
#endif
        return load_base + image_size;
    }


    const bool is_application_main = pm && name == "main";
    if (pm) {
        const auto cheats = pm->CreateCheatList(nso_header.build_id);
        if (!cheats.empty()) {
            system.RegisterCheatList(cheats, nso_header.build_id, load_base, image_size);
        }
    }

    // Load codeset for current process
    process.LoadModule(system.Kernel(), std::move(codeset), load_base);
    if (is_application_main) {


        system.SetApplicationProcessBuildID(nso_header.build_id);
        system.SetApplicationProcessMainModule(load_base + module_start, image_size - module_start);
        system.SetApplicationProcessCompanionMailbox(companion_mailbox_address);
    }
    return load_base + image_size;
}

AppLoader_NSO::LoadResult AppLoader_NSO::Load(Kernel::KProcess& process, Core::System& system) {
    if (is_loaded) {
        return {ResultStatus::ErrorAlreadyLoaded, {}};
    }

    modules.clear();

    // Load module
    const VAddr base_address = GetInteger(process.GetEntryPoint());
    if (!LoadModule(process, system, *file, base_address, true, true)) {
        return {ResultStatus::ErrorLoadingNSO, {}};
    }

    modules.insert_or_assign(base_address, file->GetName());
    LOG_DEBUG(Loader, "loaded module {} @ {:#x}", file->GetName(), base_address);

    is_loaded = true;
    return {ResultStatus::Success, LoadParameters{Kernel::KThread::DefaultThreadPriority,
                                                  Core::Memory::DEFAULT_STACK_SIZE}};
}

ResultStatus AppLoader_NSO::ReadNSOModules(Modules& out_modules) {
    out_modules = this->modules;
    return ResultStatus::Success;
}

} // namespace Loader
