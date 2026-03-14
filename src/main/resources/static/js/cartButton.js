/**
 * 
 */
document.addEventListener("DOMContentLoaded", function () {
  const cartButton = document.querySelector(".btn-cart");
  if (cartButton) {
    cartButton.addEventListener("click", function () {
      fetch("/item/isLoggedIn")
        .then(response => response.json())
        .then(isLoggedIn => {
          if (isLoggedIn) {
            window.location.href = "/cart/cartList"; // 장바구니 페이지로 이동
          } else {
            alert("로그인이 필요합니다. 로그인 페이지로 이동합니다.");
            window.location.href = "/auth/login"; // 로그인 페이지로 이동
          }
        })
        .catch(() => {
          alert("로그인 상태를 확인할 수 없습니다. 다시 시도해주세요.");
        });
    });
  }
});
