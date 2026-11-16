import type { Ref } from "react";

export function HypertubeStatement({ canvasRef, titleRef }: {
  canvasRef: Ref<HTMLCanvasElement>;
  titleRef: Ref<HTMLHeadingElement>;
}) {
  return (
    <section id="hypertube-statement" aria-labelledby="statement-title" className="hypertube-statement" lang="ar" dir="rtl">
      <canvas ref={canvasRef} className="hypertube-quote-particles" inert />
      <figure className="hypertube-quote">
        <span className="hypertube-quote-marks" aria-hidden="true" dir="ltr">“ ”</span>
        <blockquote>
          <h2 ref={titleRef} id="statement-title" className="hypertube-statement-title">
            <span className="block">ده أنا بشوف</span>{" "}
            <span className="block">أحسن منك في النور</span>{" "}
            <span className="block">وفي الضلمة كمان</span>
          </h2>
        </blockquote>
        <figcaption className="hypertube-quote-credit">
          الشيخ حسني، <cite>الكيت كات</cite> (١٩٩١)
        </figcaption>
      </figure>
    </section>
  );
}
