// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: framework/log/log.proto

package com.android.tools.idea.editors.gfxtrace.service.log;

public final class LogProtos {
  private LogProtos() {}
  public static void registerAllExtensions(
      com.google.protobuf3jarjar.ExtensionRegistry registry) {
  }
  /**
   * Protobuf enum {@code log.Severity}
   *
   * <pre>
   * Severity defines the severity of a logging message.
   * The levels match the ones defined in rfc5424 for syslog.
   * </pre>
   */
  public enum Severity
      implements com.google.protobuf3jarjar.ProtocolMessageEnum {
    /**
     * <code>Emergency = 0;</code>
     *
     * <pre>
     * Emergency indicates the system is unusable, no further data should be trusted.
     * </pre>
     */
    Emergency(0, 0),
    /**
     * <code>Alert = 1;</code>
     *
     * <pre>
     * Alert indicates action must be taken immediately.
     * </pre>
     */
    Alert(1, 1),
    /**
     * <code>Critical = 2;</code>
     *
     * <pre>
     * Critical indicates errors severe enough to terminate processing.
     * </pre>
     */
    Critical(2, 2),
    /**
     * <code>Error = 3;</code>
     *
     * <pre>
     * Error indicates non terminal failure conditions that may have an effect on results.
     * </pre>
     */
    Error(3, 3),
    /**
     * <code>Warning = 4;</code>
     *
     * <pre>
     * Warning indicates issues that might affect performance or compatibility, but could be ignored.
     * </pre>
     */
    Warning(4, 4),
    /**
     * <code>Notice = 5;</code>
     *
     * <pre>
     * Notice indicates normal but significant conditions.
     * </pre>
     */
    Notice(5, 5),
    /**
     * <code>Info = 6;</code>
     *
     * <pre>
     * Info indicates minor informational messages that should generally be ignored.
     * </pre>
     */
    Info(6, 6),
    /**
     * <code>Debug = 7;</code>
     *
     * <pre>
     * Debug indicates verbose debug-level messages.
     * </pre>
     */
    Debug(7, 7),
    UNRECOGNIZED(-1, -1),
    ;

    /**
     * <code>Emergency = 0;</code>
     *
     * <pre>
     * Emergency indicates the system is unusable, no further data should be trusted.
     * </pre>
     */
    public static final int Emergency_VALUE = 0;
    /**
     * <code>Alert = 1;</code>
     *
     * <pre>
     * Alert indicates action must be taken immediately.
     * </pre>
     */
    public static final int Alert_VALUE = 1;
    /**
     * <code>Critical = 2;</code>
     *
     * <pre>
     * Critical indicates errors severe enough to terminate processing.
     * </pre>
     */
    public static final int Critical_VALUE = 2;
    /**
     * <code>Error = 3;</code>
     *
     * <pre>
     * Error indicates non terminal failure conditions that may have an effect on results.
     * </pre>
     */
    public static final int Error_VALUE = 3;
    /**
     * <code>Warning = 4;</code>
     *
     * <pre>
     * Warning indicates issues that might affect performance or compatibility, but could be ignored.
     * </pre>
     */
    public static final int Warning_VALUE = 4;
    /**
     * <code>Notice = 5;</code>
     *
     * <pre>
     * Notice indicates normal but significant conditions.
     * </pre>
     */
    public static final int Notice_VALUE = 5;
    /**
     * <code>Info = 6;</code>
     *
     * <pre>
     * Info indicates minor informational messages that should generally be ignored.
     * </pre>
     */
    public static final int Info_VALUE = 6;
    /**
     * <code>Debug = 7;</code>
     *
     * <pre>
     * Debug indicates verbose debug-level messages.
     * </pre>
     */
    public static final int Debug_VALUE = 7;


    public final int getNumber() {
      if (index == -1) {
        throw new java.lang.IllegalArgumentException(
            "Can't get the number of an unknown enum value.");
      }
      return value;
    }

    public static Severity valueOf(int value) {
      switch (value) {
        case 0: return Emergency;
        case 1: return Alert;
        case 2: return Critical;
        case 3: return Error;
        case 4: return Warning;
        case 5: return Notice;
        case 6: return Info;
        case 7: return Debug;
        default: return null;
      }
    }

    public static com.google.protobuf3jarjar.Internal.EnumLiteMap<Severity>
        internalGetValueMap() {
      return internalValueMap;
    }
    private static final com.google.protobuf3jarjar.Internal.EnumLiteMap<
        Severity> internalValueMap =
          new com.google.protobuf3jarjar.Internal.EnumLiteMap<Severity>() {
            public Severity findValueByNumber(int number) {
              return Severity.valueOf(number);
            }
          };

    public final com.google.protobuf3jarjar.Descriptors.EnumValueDescriptor
        getValueDescriptor() {
      return getDescriptor().getValues().get(index);
    }
    public final com.google.protobuf3jarjar.Descriptors.EnumDescriptor
        getDescriptorForType() {
      return getDescriptor();
    }
    public static final com.google.protobuf3jarjar.Descriptors.EnumDescriptor
        getDescriptor() {
      return com.android.tools.idea.editors.gfxtrace.service.log.LogProtos.getDescriptor().getEnumTypes().get(0);
    }

    private static final Severity[] VALUES = values();

    public static Severity valueOf(
        com.google.protobuf3jarjar.Descriptors.EnumValueDescriptor desc) {
      if (desc.getType() != getDescriptor()) {
        throw new java.lang.IllegalArgumentException(
          "EnumValueDescriptor is not for this type.");
      }
      if (desc.getIndex() == -1) {
        return UNRECOGNIZED;
      }
      return VALUES[desc.getIndex()];
    }

    private final int index;
    private final int value;

    private Severity(int index, int value) {
      this.index = index;
      this.value = value;
    }

    // @@protoc_insertion_point(enum_scope:log.Severity)
  }


  public static com.google.protobuf3jarjar.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static com.google.protobuf3jarjar.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\027framework/log/log.proto\022\003log*k\n\010Severi" +
      "ty\022\r\n\tEmergency\020\000\022\t\n\005Alert\020\001\022\014\n\010Critical" +
      "\020\002\022\t\n\005Error\020\003\022\013\n\007Warning\020\004\022\n\n\006Notice\020\005\022\010" +
      "\n\004Info\020\006\022\t\n\005Debug\020\007B@\n3com.android.tools" +
      ".idea.editors.gfxtrace.service.logB\tLogP" +
      "rotosb\006proto3"
    };
    com.google.protobuf3jarjar.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
        new com.google.protobuf3jarjar.Descriptors.FileDescriptor.    InternalDescriptorAssigner() {
          public com.google.protobuf3jarjar.ExtensionRegistry assignDescriptors(
              com.google.protobuf3jarjar.Descriptors.FileDescriptor root) {
            descriptor = root;
            return null;
          }
        };
    com.google.protobuf3jarjar.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf3jarjar.Descriptors.FileDescriptor[] {
        }, assigner);
  }

  // @@protoc_insertion_point(outer_class_scope)
}
