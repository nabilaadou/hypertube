export type Movie = {
  title: string;
  awardYear: number;
  award: string;
  poster: string;
  trailerId: string;
};

export const draftMovies = [
  {
    title: "Oppenheimer",
    awardYear: 2024,
    award: "Best Picture",
    poster: "https://image.tmdb.org/t/p/w500/ptpr0kGAckfQkJeJIt8st5dglvd.jpg",
    trailerId: "bK6ldnjE3Y0",
  },
  {
    title: "Everything Everywhere All at Once",
    awardYear: 2023,
    award: "Best Picture",
    poster: "https://image.tmdb.org/t/p/w500/w3LxiVYdWWRvEVdn5RYq6jIqkb1.jpg",
    trailerId: "wxN1T1uxQ2g",
  },
  {
    title: "CODA",
    awardYear: 2022,
    award: "Best Picture",
    poster: "https://image.tmdb.org/t/p/w500/BzVjmm8l23rPsijLiNLUzuQtyd.jpg",
    trailerId: "0pmfrE1YL4I",
  },
  {
    title: "Parasite",
    awardYear: 2020,
    award: "Best Picture",
    poster: "https://image.tmdb.org/t/p/w500/7IiTTgloJzvGI1TAYymCfbfl3vT.jpg",
    trailerId: "isOGD_7hNIY",
  },
] satisfies Movie[];

export const cinemaMedia = {
  edit: "/media/reel.mp4",
  poster: "/media/reel-poster.jpg",
  theaterImage: "/media/theater.png",
  theaterModel: "/models/private-home-cinema-textured.glb",
};
