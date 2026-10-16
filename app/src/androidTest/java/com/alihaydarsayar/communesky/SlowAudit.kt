package com.alihaydarsayar.communesky

/** Çok uzun süren denetim testleri; varsayılan androidTest çalıştırmasında atlanır (README: "Slow widget audit"). */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class SlowAudit
