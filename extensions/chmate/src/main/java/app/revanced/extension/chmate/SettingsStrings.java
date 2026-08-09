package app.revanced.extension.chmate;

import java.util.Locale;

final class SettingsStrings {
    final String title;
    final String description;
    final String userAgent;
    final String hint;
    final String save;
    final String saveAndRestart;
    final String restart;
    final String reset;
    final String saved;
    final String saveFailed;
    final String restartFailed;
    final String invalid;
    final boolean rightToLeft;

    private SettingsStrings(
            String title,
            String description,
            String userAgent,
            String hint,
            String save,
            String saveAndRestart,
            String restart,
            String reset,
            String saved,
            String saveFailed,
            String restartFailed,
            String invalid,
            boolean rightToLeft
    ) {
        this.title = title;
        this.description = description;
        this.userAgent = userAgent;
        this.hint = hint;
        this.save = save;
        this.saveAndRestart = saveAndRestart;
        this.restart = restart;
        this.reset = reset;
        this.saved = saved;
        this.saveFailed = saveFailed;
        this.restartFailed = restartFailed;
        this.invalid = invalid;
        this.rightToLeft = rightToLeft;
    }

    static SettingsStrings current() {
        String language = Locale.getDefault().getLanguage();
        switch (language) {
            case "ja":
                return text("ChMate ReVanced 設定", "空欄ではChMate本来のUser-Agentを使用します。変更後は再起動してください。", "User-Agent", "例: Monazilla/1.00 ...", "保存", "保存して再起動", "今すぐ再起動", "既定値に戻す", "保存しました", "保存できませんでした", "ChMateを再起動できませんでした", "改行を含まない512文字以内で入力してください", false);
            case "zh":
                return text("ChMate ReVanced 设置", "留空将使用 ChMate 的原始 User-Agent。更改后请重启。", "User-Agent", "例如：Monazilla/1.00 ...", "保存", "保存并重启", "立即重启", "恢复默认", "已保存", "保存失败", "无法重启 ChMate", "请输入不含换行且不超过 512 个字符的内容", false);
            case "hi":
                return text("ChMate ReVanced सेटिंग", "खाली छोड़ने पर ChMate का मूल User-Agent उपयोग होगा। बदलाव के बाद पुनः आरंभ करें।", "User-Agent", "उदाहरण: Monazilla/1.00 ...", "सहेजें", "सहेजें और पुनः आरंभ करें", "अभी पुनः आरंभ करें", "डिफ़ॉल्ट बहाल करें", "सहेजा गया", "सहेजना विफल रहा", "ChMate को पुनः आरंभ नहीं किया जा सका", "बिना नई पंक्ति के अधिकतम 512 अक्षर दर्ज करें", false);
            case "es":
                return text("Ajustes de ChMate ReVanced", "Si se deja vacío, se usa el User-Agent original de ChMate. Reinicia después de cambiarlo.", "User-Agent", "Ejemplo: Monazilla/1.00 ...", "Guardar", "Guardar y reiniciar", "Reiniciar ahora", "Restaurar valor", "Guardado", "No se pudo guardar", "No se pudo reiniciar ChMate", "Introduce hasta 512 caracteres sin saltos de línea", false);
            case "fr":
                return text("Paramètres ChMate ReVanced", "Laissez vide pour utiliser le User-Agent d’origine de ChMate. Redémarrez après modification.", "User-Agent", "Exemple : Monazilla/1.00 ...", "Enregistrer", "Enregistrer et redémarrer", "Redémarrer", "Rétablir", "Enregistré", "Échec de l’enregistrement", "Impossible de redémarrer ChMate", "Saisissez au plus 512 caractères sans retour à la ligne", false);
            case "ar":
                return text("إعدادات ChMate ReVanced", "اترك الحقل فارغًا لاستخدام User-Agent الأصلي. أعد التشغيل بعد التغيير.", "User-Agent", "مثال: Monazilla/1.00 ...", "حفظ", "حفظ وإعادة التشغيل", "إعادة التشغيل الآن", "استعادة الافتراضي", "تم الحفظ", "تعذر الحفظ", "تعذرت إعادة تشغيل ChMate", "أدخل 512 حرفًا كحد أقصى دون أسطر جديدة", true);
            case "pt":
                return text("Configurações do ChMate ReVanced", "Deixe em branco para usar o User-Agent original do ChMate. Reinicie após alterar.", "User-Agent", "Exemplo: Monazilla/1.00 ...", "Salvar", "Salvar e reiniciar", "Reiniciar agora", "Restaurar padrão", "Salvo", "Falha ao salvar", "Não foi possível reiniciar o ChMate", "Digite até 512 caracteres sem quebras de linha", false);
            case "bn":
                return text("ChMate ReVanced সেটিংস", "ফাঁকা রাখলে ChMate-এর মূল User-Agent ব্যবহৃত হবে। পরিবর্তনের পরে পুনরায় চালু করুন।", "User-Agent", "উদাহরণ: Monazilla/1.00 ...", "সংরক্ষণ", "সংরক্ষণ ও পুনরায় চালু", "এখন পুনরায় চালু", "ডিফল্ট ফিরিয়ে দিন", "সংরক্ষিত", "সংরক্ষণ ব্যর্থ হয়েছে", "ChMate পুনরায় চালু করা যায়নি", "নতুন লাইন ছাড়া সর্বোচ্চ ৫১২ অক্ষর লিখুন", false);
            case "ru":
                return text("Настройки ChMate ReVanced", "Оставьте поле пустым, чтобы использовать исходный User-Agent ChMate. После изменения перезапустите приложение.", "User-Agent", "Пример: Monazilla/1.00 ...", "Сохранить", "Сохранить и перезапустить", "Перезапустить", "Сбросить", "Сохранено", "Не удалось сохранить", "Не удалось перезапустить ChMate", "Введите не более 512 символов без переноса строк", false);
            case "ur":
                return text("ChMate ReVanced ترتیبات", "خالی چھوڑنے پر ChMate کا اصل User-Agent استعمال ہوگا۔ تبدیلی کے بعد دوبارہ شروع کریں۔", "User-Agent", "مثال: Monazilla/1.00 ...", "محفوظ کریں", "محفوظ کر کے دوبارہ شروع کریں", "ابھی دوبارہ شروع کریں", "ڈیفالٹ بحال کریں", "محفوظ ہوگیا", "محفوظ نہیں ہو سکا", "ChMate کو دوبارہ شروع نہیں کیا جا سکا", "نئی سطر کے بغیر زیادہ سے زیادہ 512 حروف درج کریں", true);
            default:
                return text("ChMate ReVanced settings", "Leave blank to use ChMate's original User-Agent. Restart after changing it.", "User-Agent", "Example: Monazilla/1.00 ...", "Save", "Save and restart", "Restart now", "Restore default", "Saved", "Save failed", "Could not restart ChMate", "Enter at most 512 characters without line breaks", false);
        }
    }

    private static SettingsStrings text(
            String title, String description, String userAgent, String hint,
            String save, String saveAndRestart, String restart, String reset,
            String saved, String saveFailed, String restartFailed, String invalid, boolean rightToLeft
    ) {
        return new SettingsStrings(title, description, userAgent, hint, save,
                saveAndRestart, restart, reset, saved, saveFailed, restartFailed, invalid, rightToLeft);
    }
}
