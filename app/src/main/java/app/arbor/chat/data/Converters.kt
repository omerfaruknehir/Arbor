package app.arbor.chat.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun fromRole(value: MessageRole): String = value.name
    @TypeConverter fun toRole(value: String): MessageRole = MessageRole.valueOf(value)
    @TypeConverter fun fromStatus(value: MessageStatus): String = value.name
    @TypeConverter fun toStatus(value: String): MessageStatus = MessageStatus.valueOf(value)
    @TypeConverter fun fromProviderKind(value: ProviderKind): String = value.name
    @TypeConverter fun toProviderKind(value: String): ProviderKind = ProviderKind.valueOf(value)
    @TypeConverter fun fromReasoningVisibility(value: ReasoningVisibility): String = value.name
    @TypeConverter fun toReasoningVisibility(value: String): ReasoningVisibility =
        if (value == "HIDE") ReasoningVisibility.COLLAPSED else ReasoningVisibility.valueOf(value)
    @TypeConverter fun fromAuxiliaryMode(value: AuxiliaryMode): String = value.name
    @TypeConverter fun toAuxiliaryMode(value: String): AuxiliaryMode = AuxiliaryMode.valueOf(value)
}
