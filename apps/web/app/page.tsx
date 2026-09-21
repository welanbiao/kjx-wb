import Link from "next/link";

export default function HomePage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-3xl flex-col justify-center gap-6 px-6 py-16">
      <p className="text-sm font-medium tracking-wide text-slate-500">WP-00C · apps/web</p>
      <h1 className="text-4xl font-semibold tracking-tight">叩君芯会员前台骨架</h1>
      <p className="max-w-2xl text-base leading-7 text-slate-600">
        Next.js 16 + React 19 + Tailwind 4 的最小可运行壳。注册、登录、绑定与解锁由 WP-20
        接入；身份凭证只走 httpOnly Cookie，前端不保存 token。
      </p>
      <div className="flex flex-wrap gap-3">
        <Link
          href="/health"
          className="rounded-full bg-slate-900 px-4 py-2 text-sm font-medium text-white"
        >
          健康检查
        </Link>
        <a
          href="https://nextjs.org"
          className="rounded-full border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700"
        >
          Next.js 文档
        </a>
      </div>
    </main>
  );
}
