export type Movie = {
  title: string;
  awardYear: number;
  award: string;
  poster: string;
  trailerId: string;
};

export const bestPictureWinners = [
  {
    title: "Kramer vs. Kramer",
    awardYear: 1980,
    award: "Best Picture",
    poster: "https://media.senscritique.com/media/000016148327/0/kramer_contre_kramer.jpg",
    trailerId: "OXw67SqTtNg",
  },
  {
    title: "Amadeus",
    awardYear: 1985,
    award: "Best Picture",
    poster: "https://cameralabs.org/aeonmedia/zenfoto/12441/99027/amadej-amadeus-1984-rezhissjor-milosh-forman-amerikanskij-poster-k_original.jpg",
    trailerId: "POe2mmWaR0k",
  },
  {
    title: "The Silence of the Lambs",
    awardYear: 1992,
    award: "Best Picture",
    poster: "https://image.tmdb.org/t/p/w500/uS9m8OBk1A8eM9I042bx8XXpqAq.jpg",
    trailerId: "6iB21hsprAQ",
  },
  {
    title: "Schindler’s List",
    awardYear: 1994,
    award: "Best Picture",
    poster: "https://image.tmdb.org/t/p/w500/sF1U4EUQS8YHUYjNl3pMGNIQyr0.jpg",
    trailerId: "JdRGC-w9syA",
  },
  {
    title: "The Lord of the Rings: The Return of the King",
    awardYear: 2004,
    award: "Best Picture",
    poster: "https://image.tmdb.org/t/p/w500/rCzpDGLbOoPwLjy3OAm5NUPOTrC.jpg",
    trailerId: "r5X-hFf6Bwo",
  },
  {
    title: "No Country for Old Men",
    awardYear: 2008,
    award: "Best Picture",
    poster: "https://image.tmdb.org/t/p/w500/zuArI6EBpV6mExrvfnyidoOtzdf.jpg",
    trailerId: "A0oNrgumrlE",
  },
  {
    title: "Moonlight",
    awardYear: 2017,
    award: "Best Picture",
    poster: "https://filmartgallery.com/cdn/shop/files/Moonlight-Vintage-Movie-Poster-Original_1200x.jpg?v=1771967190",
    trailerId: "9NJj12tJzqc",
  },
  {
    title: "Parasite",
    awardYear: 2020,
    award: "Best Picture",
    poster: "https://image.tmdb.org/t/p/w500/7IiTTgloJzvGI1TAYymCfbfl3vT.jpg",
    trailerId: "isOGD_7hNIY",
  },
  {
    title: "Oppenheimer",
    awardYear: 2024,
    award: "Best Picture",
    poster: "https://image.tmdb.org/t/p/w500/ptpr0kGAckfQkJeJIt8st5dglvd.jpg",
    trailerId: "bK6ldnjE3Y0",
  },
  {
    title: "One Battle After Another",
    awardYear: 2026,
    award: "Best Picture",
    poster: "https://image.tmdb.org/t/p/w500/m1jFoahEbeQXtx4zArT2FKdbNIj.jpg",
    trailerId: "kQUPdVxZNPk",
  },
] satisfies [Movie, ...Movie[]];

export const cinemaMedia = {
  edit: "/media/reel.mp4",
  poster: "/media/reel-poster.jpg",
  theaterImage: "/media/theater.png",
  theaterModel: "/models/private-home-cinema-textured.glb",
};
