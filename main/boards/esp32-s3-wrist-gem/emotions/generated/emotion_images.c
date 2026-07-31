#include "emotion_images.h"

#include <string.h>

typedef struct {
  const char* name;
  const lv_image_dsc_t* img;
} emotion_image_entry_t;

static const emotion_image_entry_t kEmotionImages[] = {
  {"neutral", &emotion_neutral},
  {"happy", &emotion_happy},
  {"laughing", &emotion_laughing},
  {"funny", &emotion_funny},
  {"sad", &emotion_sad},
  {"angry", &emotion_angry},
  {"crying", &emotion_crying},
  {"loving", &emotion_loving},
  {"embarrassed", &emotion_embarrassed},
  {"surprised", &emotion_surprised},
  {"shocked", &emotion_shocked},
  {"thinking", &emotion_thinking},
  {"winking", &emotion_winking},
  {"cool", &emotion_cool},
  {"relaxed", &emotion_relaxed},
  {"delicious", &emotion_delicious},
  {"kissy", &emotion_kissy},
  {"confident", &emotion_confident},
  {"sleepy", &emotion_sleepy},
  {"silly", &emotion_silly},
  {"confused", &emotion_confused},
};

const lv_image_dsc_t* FindEmotionImage(const char* emotion) {
  if (emotion == NULL) {
    return NULL;
  }
  for (unsigned i = 0; i < sizeof(kEmotionImages) / sizeof(kEmotionImages[0]); ++i) {
    if (strcmp(emotion, kEmotionImages[i].name) == 0) {
      return kEmotionImages[i].img;
    }
  }
  return NULL;
}
