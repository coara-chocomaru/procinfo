LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE           := proc
LOCAL_SRC_FILES        := proc.c
include $(BUILD_SHARED_LIBRARY)
