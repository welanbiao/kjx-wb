#pragma once

#include <lvgl.h>

#ifdef __cplusplus
extern "C" {
#endif

const lv_image_dsc_t* FindEmotionImage(const char* emotion);

extern const lv_image_dsc_t emotion_neutral;
extern const lv_image_dsc_t emotion_happy;
extern const lv_image_dsc_t emotion_laughing;
extern const lv_image_dsc_t emotion_funny;
extern const lv_image_dsc_t emotion_sad;
extern const lv_image_dsc_t emotion_angry;
extern const lv_image_dsc_t emotion_crying;
extern const lv_image_dsc_t emotion_loving;
extern const lv_image_dsc_t emotion_embarrassed;
extern const lv_image_dsc_t emotion_surprised;
extern const lv_image_dsc_t emotion_shocked;
extern const lv_image_dsc_t emotion_thinking;
extern const lv_image_dsc_t emotion_winking;
extern const lv_image_dsc_t emotion_cool;
extern const lv_image_dsc_t emotion_relaxed;
extern const lv_image_dsc_t emotion_delicious;
extern const lv_image_dsc_t emotion_kissy;
extern const lv_image_dsc_t emotion_confident;
extern const lv_image_dsc_t emotion_sleepy;
extern const lv_image_dsc_t emotion_silly;
extern const lv_image_dsc_t emotion_confused;

#ifdef __cplusplus
}
#endif
