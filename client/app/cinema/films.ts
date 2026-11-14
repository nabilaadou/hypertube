export const media = {
  reel: process.env.NEXT_PUBLIC_CINEMA_REEL || "/cinema/reel.mp4",
  still: process.env.NEXT_PUBLIC_CINEMA_STILL || "/cinema/reel-poster.jpg",
  room: process.env.NEXT_PUBLIC_CINEMA_ROOM || "/cinema/theater.png",
  model: process.env.NEXT_PUBLIC_CINEMA_MODEL || "/cinema/private-home-cinema-textured.glb",
};

export const films = [
  {
    title: "Oppenheimer",
    year: 2024,
    award: "Best Picture",
    poster: "ptpr0kGAckfQkJeJIt8st5dglvd.jpg",
    trailer: "bK6ldnjE3Y0",
    description: "A physicist races to build the first atomic bomb and reckons with the consequences.",
  },
  {
    title: "Everything Everywhere All at Once",
    year: 2023,
    award: "Best Picture",
    poster: "w3LxiVYdWWRvEVdn5RYq6jIqkb1.jpg",
    trailer: "wxN1T1uxQ2g",
    description: "A laundromat owner discovers that the fate of the multiverse rests on the lives she could have lived.",
  },
  {
    title: "CODA",
    year: 2022,
    award: "Best Picture",
    poster: "BzVjmm8l23rPsijLiNLUzuQtyd.jpg",
    trailer: "0pmfrE1YL4I",
    description: "The only hearing member of her family must choose between their fishing business and her love of singing.",
  },
  {
    title: "Nomadland",
    year: 2021,
    award: "Best Picture",
    poster: "kHp4nX4y9enY2HYpG1d8hLgajnv.jpg",
    trailer: "6sxCFZ8_d84",
    description: "After her company town closes, a widow finds community among people living on the road across the American West.",
  },
  {
    title: "Parasite",
    year: 2020,
    award: "Best Picture",
    poster: "7IiTTgloJzvGI1TAYymCfbfl3vT.jpg",
    trailer: "isOGD_7hNIY",
    description: "A family takes jobs in a wealthy household, where their plans unravel around a concealed secret.",
  },
] as const;

export type Film = (typeof films)[number];
